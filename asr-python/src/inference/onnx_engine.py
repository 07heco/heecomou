import io
import wave
import base64
import logging
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import numpy as np
import onnxruntime as ort

logger = logging.getLogger(__name__)

EOS_TOKEN_ID = 50257
SOT_TOKEN_ID = 50258
TRANSCRIBE_TOKEN_ID = 50359
TRANSLATE_TOKEN_ID = 50358
ZH_LANG_TOKEN_ID = 50260
NO_TIMESTAMPS_TOKEN_ID = 50363
MAX_DECODER_LENGTH = 448


@dataclass
class OnnxRecognitionResult:
    text: str
    duration_ms: float


class WhisperOnnxEngine:
    def __init__(self, model_dir: str):
        self.model_dir = Path(model_dir)
        self._encoder_session: Optional[ort.InferenceSession] = None
        self._decoder_session: Optional[ort.InferenceSession] = None
        self._feature_extractor = None
        self._tokenizer = None
        self._loaded = False

    @property
    def is_loaded(self) -> bool:
        return self._loaded

    def load(self):
        if self._loaded:
            return

        logger.info("Loading Whisper ONNX models from %s...", self.model_dir)

        ort.set_default_logger_severity(3)

        encoder_path = self._find_model("encoder_int8.onnx", "encoder.onnx")
        decoder_path = self._find_model("decoder_int8.onnx", "decoder.onnx")

        sess_options = ort.SessionOptions()
        sess_options.graph_optimization_level = (
            ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        )
        sess_options.intra_op_num_threads = 4

        self._encoder_session = ort.InferenceSession(
            encoder_path, sess_options, providers=["CPUExecutionProvider"]
        )
        self._decoder_session = ort.InferenceSession(
            decoder_path, sess_options, providers=["CPUExecutionProvider"]
        )

        from transformers import WhisperFeatureExtractor, WhisperTokenizer

        self._feature_extractor = WhisperFeatureExtractor.from_pretrained(
            str(self.model_dir)
        )
        self._tokenizer = WhisperTokenizer.from_pretrained(
            str(self.model_dir)
        )

        self._loaded = True
        logger.info("ONNX Whisper engine loaded successfully")

    def unload(self):
        if self._encoder_session is not None:
            del self._encoder_session
            self._encoder_session = None
        if self._decoder_session is not None:
            del self._decoder_session
            self._decoder_session = None
        self._feature_extractor = None
        self._tokenizer = None
        self._loaded = False

    def recognize(self, audio_b64: str, language: str = "zh") -> OnnxRecognitionResult:
        if not self._loaded:
            raise RuntimeError("Model not loaded. Call load() first.")

        start_time = time.time()

        audio_np, sample_rate = self._decode_audio(audio_b64)

        input_features = self._feature_extractor(
            audio_np,
            sampling_rate=sample_rate,
            return_tensors="np",
        ).input_features.astype(np.float32)

        encoder_outputs = self._encoder_session.run(
            None, {"input_features": input_features}
        )[0]

        lang_token = ZH_LANG_TOKEN_ID
        decoder_input_ids = [
            SOT_TOKEN_ID,
            lang_token,
            TRANSCRIBE_TOKEN_ID,
            NO_TIMESTAMPS_TOKEN_ID,
        ]

        eos = EOS_TOKEN_ID

        for _ in range(MAX_DECODER_LENGTH):
            ids_array = np.array([decoder_input_ids], dtype=np.int64)
            logits = self._decoder_session.run(
                None,
                {
                    "input_ids": ids_array,
                    "encoder_hidden_states": encoder_outputs,
                },
            )[0]

            next_token_logits = logits[0, -1, :]
            next_token_id = int(np.argmax(next_token_logits))

            if next_token_id == eos:
                break

            decoder_input_ids.append(next_token_id)

        text = self._tokenizer.decode(
            decoder_input_ids[4:], skip_special_tokens=True
        ).strip()

        import zhconv
        text = zhconv.convert(text, "zh-cn")

        duration_ms = (time.time() - start_time) * 1000.0

        logger.info("ONNX recognition completed in %.0fms: %s", duration_ms, text[:50])
        return OnnxRecognitionResult(text=text, duration_ms=duration_ms)

    def _find_model(self, *preferences: str) -> str:
        for name in preferences:
            p = str(self.model_dir / name)
            if Path(p).exists():
                logger.info("Using model: %s", name)
                return p
        raise FileNotFoundError(
            f"No model found in {self.model_dir}. Tried: {preferences}"
        )

    def _decode_audio(self, audio_b64: str):
        raw = base64.b64decode(audio_b64)
        buf = io.BytesIO(raw)

        try:
            import soundfile as sf
            audio, sample_rate = sf.read(buf, dtype="float32")
        except Exception:
            buf.seek(0)
            with wave.open(buf, "rb") as wf:
                nchannels = wf.getnchannels()
                sample_width = wf.getsampwidth()
                framerate = wf.getframerate()
                nframes = wf.getnframes()
                raw_data = wf.readframes(nframes)
                if sample_width == 2:
                    dtype = np.int16
                elif sample_width == 4:
                    dtype = np.int32
                else:
                    dtype = np.uint8
                audio = np.frombuffer(raw_data, dtype=dtype).astype(np.float32)
                max_val = np.iinfo(dtype).max
                audio = audio / max_val if max_val > 0 else audio
                if nchannels > 1:
                    audio = audio.reshape(-1, nchannels).mean(axis=1)
                sample_rate = framerate

        if sample_rate != 16000:
            logger.info("Resampling audio from %d Hz to 16000 Hz", sample_rate)
            import scipy.signal
            num_samples = int(len(audio) * 16000 / sample_rate)
            audio = scipy.signal.resample(audio, num_samples)

        audio = audio.astype(np.float32)
        return audio, 16000
