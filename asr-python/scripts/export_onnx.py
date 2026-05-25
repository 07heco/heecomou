import argparse
import json
import logging
import os
import sys
import time
from pathlib import Path


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("export_onnx")

MODEL_ID_DEFAULT = "openai/whisper-small"
OUTPUT_DIR_DEFAULT = "onnx_models"
SAMPLE_RATE = 16000
N_MELS = 80
MAX_SOURCE_POSITIONS = 3000
MAX_TARGET_POSITIONS = 448


class ONNXExporter:
    def __init__(
        self,
        model_id: str = MODEL_ID_DEFAULT,
        output_dir: str = OUTPUT_DIR_DEFAULT,
        device: str = "cpu",
    ):
        self.model_id = model_id
        self.output_dir = Path(output_dir)
        self.device = device
        self._model = None
        self._processor = None
        self._torch = None

    def _ensure_torch(self):
        if self._torch is None:
            import torch
            self._torch = torch
        return self._torch

    def _load_model(self):
        if self._model is not None:
            return

        torch = self._ensure_torch()
        from transformers import (
            AutoModelForSpeechSeq2Seq,
            WhisperProcessor,
        )

        logger.info("Loading model %s on %s...", self.model_id, self.device)

        self._processor = WhisperProcessor.from_pretrained(self.model_id)

        torch_dtype = (
            torch.float16 if self.device.startswith("cuda") else torch.float32
        )
        self._model = AutoModelForSpeechSeq2Seq.from_pretrained(
            self.model_id,
            torch_dtype=torch_dtype,
            low_cpu_mem_usage=True,
        ).to(self.device)
        self._model.eval()

        logger.info("Model loaded successfully")

    def _prepare_output_dir(self):
        self.output_dir.mkdir(parents=True, exist_ok=True)
        logger.info("Output directory: %s", self.output_dir.resolve())

    def export_encoder(self) -> str:
        torch = self._ensure_torch()

        encoder = self._model.get_encoder()

        dummy_input = torch.randn(1, N_MELS, MAX_SOURCE_POSITIONS, device=self.device)

        output_path = str(self.output_dir / "encoder.onnx")

        logger.info("Exporting encoder to %s...", output_path)
        start = time.time()

        torch.onnx.export(
            encoder,
            dummy_input,
            output_path,
            input_names=["input_features"],
            output_names=["encoder_hidden_states"],
            dynamic_axes={
                "input_features": {0: "batch", 2: "sequence"},
                "encoder_hidden_states": {0: "batch", 1: "encoder_sequence"},
            },
            opset_version=17,
            do_constant_folding=True,
        )

        elapsed = time.time() - start
        file_size = os.path.getsize(output_path) / (1024 * 1024)
        logger.info(
            "Encoder exported in %.1fs, size: %.1f MB", elapsed, file_size
        )
        return output_path

    def export_decoder(self) -> str:
        torch = self._ensure_torch()

        model_config = self._model.config
        hidden_size = model_config.d_model
        vocab_size = model_config.vocab_size

        dummy_encoder_hidden = torch.randn(
            1, MAX_SOURCE_POSITIONS, hidden_size, device=self.device
        )
        dummy_input_ids = torch.randint(
            0, vocab_size, (1, 1), device=self.device
        )

        class WhisperDecoderWrapper(torch.nn.Module):
            def __init__(self, whisper_model):
                super().__init__()
                self.decoder = whisper_model.model.decoder
                self.proj_out = whisper_model.proj_out

            def forward(self, input_ids, encoder_hidden_states):
                hidden = self.decoder(
                    input_ids=input_ids,
                    encoder_hidden_states=encoder_hidden_states,
                ).last_hidden_state
                logits = self.proj_out(hidden)
                return logits

        wrapped = WhisperDecoderWrapper(self._model)
        wrapped.eval()

        output_path = str(self.output_dir / "decoder.onnx")

        logger.info("Exporting decoder to %s...", output_path)
        start = time.time()

        torch.onnx.export(
            wrapped,
            (dummy_input_ids, dummy_encoder_hidden),
            output_path,
            input_names=["input_ids", "encoder_hidden_states"],
            output_names=["logits"],
            dynamic_axes={
                "input_ids": {0: "batch", 1: "decoder_sequence"},
                "encoder_hidden_states": {0: "batch", 1: "encoder_sequence"},
                "logits": {0: "batch", 1: "decoder_sequence"},
            },
            opset_version=17,
            do_constant_folding=True,
        )

        elapsed = time.time() - start
        file_size = os.path.getsize(output_path) / (1024 * 1024)
        logger.info(
            "Decoder exported in %.1fs, size: %.1f MB", elapsed, file_size
        )
        return output_path

    def _apply_dynamic_quantization(self, onnx_path: str) -> str:
        try:
            from onnxruntime.quantization import quantize_dynamic, QuantType
        except ImportError:
            logger.warning(
                "onnxruntime quantization tools not available, "
                "skipping INT8 quantization"
            )
            return onnx_path

        quant_path = str(self.output_dir / Path(onnx_path).name.replace(
            ".onnx", "_int8.onnx"
        ))

        logger.info("Applying INT8 dynamic quantization to %s...", onnx_path)
        start = time.time()

        quantize_dynamic(
            model_input=onnx_path,
            model_output=quant_path,
            weight_type=QuantType.QInt8,
        )

        elapsed = time.time() - start
        orig_size = os.path.getsize(onnx_path) / (1024 * 1024)
        quant_size = os.path.getsize(quant_path) / (1024 * 1024)
        reduction = (1 - quant_size / orig_size) * 100
        logger.info(
            "Quantized in %.1fs: %.1f MB → %.1f MB (%.0f%% reduction)",
            elapsed, orig_size, quant_size, reduction,
        )
        return quant_path

    def save_tokenizer(self) -> str:
        self.output_dir.mkdir(parents=True, exist_ok=True)
        if self._processor is not None and hasattr(self._processor, "tokenizer"):
            self._processor.tokenizer.save_pretrained(str(self.output_dir))
            self._processor.feature_extractor.save_pretrained(str(self.output_dir))
            logger.info("Tokenizer + feature_extractor saved to %s", self.output_dir)
        else:
            logger.warning("Processor not available, saving placeholder")
            output_path = str(self.output_dir / "tokenizer.json")
            with open(output_path, "w", encoding="utf-8") as f:
                json.dump({"model_id": self.model_id}, f)

        return str(self.output_dir / "tokenizer.json")

    def save_export_info(self, files: dict):
        info = {
            "model_id": self.model_id,
            "device": self.device,
            "files": files,
        }
        info_path = str(self.output_dir / "export_info.json")
        with open(info_path, "w", encoding="utf-8") as f:
            json.dump(info, f, indent=2, ensure_ascii=False)
        logger.info("Export info saved to %s", info_path)

    def run(self) -> dict:
        self._prepare_output_dir()

        try:
            self._load_model()
        except Exception as e:
            logger.error("Failed to load model: %s", e)
            logger.info(
                "Hint: Ensure the model is available. "
                "You may need to download it first with:\n"
                "  python -c \"from transformers import AutoModelForSpeechSeq2Seq; "
                "AutoModelForSpeechSeq2Seq.from_pretrained('%s')\"\n"
                "Or set HF_ENDPOINT=https://hf-mirror.com for Chinese users.",
                self.model_id,
            )
            raise

        files = {}

        encoder_path = self.export_encoder()
        encoder_int8_path = self._apply_dynamic_quantization(encoder_path)
        files["encoder"] = os.path.basename(encoder_path)
        files["encoder_int8"] = os.path.basename(encoder_int8_path)

        decoder_path = self.export_decoder()
        decoder_int8_path = self._apply_dynamic_quantization(decoder_path)
        files["decoder"] = os.path.basename(decoder_path)
        files["decoder_int8"] = os.path.basename(decoder_int8_path)

        tokenizer_path = self.save_tokenizer()
        files["tokenizer"] = os.path.basename(tokenizer_path)
        tokenizer_dir = str(self.output_dir)

        self.save_export_info(files)

        total_size = sum(
            os.path.getsize(str(self.output_dir / f))
            for f in [
                files["encoder_int8"],
                files["decoder_int8"],
            ]
            if (self.output_dir / f).exists()
        ) / (1024 * 1024)

        logger.info("=" * 60)
        logger.info("Export complete!")
        logger.info("  Output directory: %s", self.output_dir.resolve())
        logger.info(
            "  Encoder (INT8):    %s (%.1f MB)",
            files["encoder_int8"],
            os.path.getsize(
                str(self.output_dir / files["encoder_int8"])
            ) / (1024 * 1024),
        )
        logger.info(
            "  Decoder (INT8):    %s (%.1f MB)",
            files["decoder_int8"],
            os.path.getsize(
                str(self.output_dir / files["decoder_int8"])
            ) / (1024 * 1024),
        )
        logger.info("  Tokenizer:         %s/", tokenizer_dir)
        logger.info("  Total model size:  %.1f MB", total_size)
        logger.info("=" * 60)

        return files


def main():
    parser = argparse.ArgumentParser(
        description="Export Whisper model to ONNX with INT8 quantization"
    )
    parser.add_argument(
        "--model-id",
        default=MODEL_ID_DEFAULT,
        help=f"Model ID on HuggingFace (default: {MODEL_ID_DEFAULT})",
    )
    parser.add_argument(
        "--output-dir",
        default=OUTPUT_DIR_DEFAULT,
        help=f"Output directory (default: {OUTPUT_DIR_DEFAULT})",
    )
    parser.add_argument(
        "--device",
        default="cpu",
        choices=["cpu", "cuda", "cuda:0"],
        help="Device to run export on (default: cpu)",
    )
    parser.add_argument(
        "--skip-quantize",
        action="store_true",
        help="Skip INT8 quantization, export FP32 only",
    )
    parser.add_argument(
        "--encoder-only",
        action="store_true",
        help="Export encoder only",
    )
    parser.add_argument(
        "--decoder-only",
        action="store_true",
        help="Export decoder only",
    )

    args = parser.parse_args()

    exporter = ONNXExporter(
        model_id=args.model_id,
        output_dir=args.output_dir,
        device=args.device,
    )

    try:
        exporter._prepare_output_dir()
        exporter._load_model()

        if args.encoder_only:
            encoder_path = exporter.export_encoder()
            if not args.skip_quantize:
                exporter._apply_dynamic_quantization(encoder_path)
            exporter.save_tokenizer()
        elif args.decoder_only:
            decoder_path = exporter.export_decoder()
            if not args.skip_quantize:
                exporter._apply_dynamic_quantization(decoder_path)
        else:
            files = exporter.run()
            logger.info("All exports completed: %s", files)
    except Exception as e:
        logger.error("Export failed: %s", e)
        sys.exit(1)


if __name__ == "__main__":
    main()
