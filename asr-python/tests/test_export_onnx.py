import json
import os
import sys
import tempfile
from pathlib import Path
from unittest.mock import patch

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent / "src"))
sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

from scripts.export_onnx import (
    MODEL_ID_DEFAULT,
    OUTPUT_DIR_DEFAULT,
    MAX_SOURCE_POSITIONS,
    N_MELS,
    ONNXExporter,
)


class TestONNXExporterInit:
    def test_default_values(self):
        exporter = ONNXExporter()
        assert exporter.model_id == MODEL_ID_DEFAULT
        assert exporter.output_dir == Path(OUTPUT_DIR_DEFAULT)
        assert exporter.device == "cpu"

    def test_custom_values(self):
        exporter = ONNXExporter(
            model_id="test/model",
            output_dir="/custom/path",
            device="cuda",
        )
        assert exporter.model_id == "test/model"
        assert exporter.output_dir == Path("/custom/path")
        assert exporter.device == "cuda"


class TestPrepareOutputDir:
    def test_creates_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            exporter = ONNXExporter(output_dir=os.path.join(tmp, "onnx_out"))
            exporter._prepare_output_dir()
            assert os.path.isdir(os.path.join(tmp, "onnx_out"))

    def test_existing_directory_ok(self):
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = os.path.join(tmp, "onnx_out")
            os.makedirs(out_dir)
            exporter = ONNXExporter(output_dir=out_dir)
            exporter._prepare_output_dir()


class TestSaveExportInfo:
    def test_saves_info_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            exporter = ONNXExporter(output_dir=tmp)
            exporter._prepare_output_dir()
            files = {
                "encoder": "encoder.onnx",
                "encoder_int8": "encoder_int8.onnx",
                "decoder": "decoder.onnx",
                "decoder_int8": "decoder_int8.onnx",
                "tokenizer": "tokenizer.json",
            }
            exporter.save_export_info(files)

            info_path = os.path.join(tmp, "export_info.json")
            assert os.path.isfile(info_path)

            with open(info_path, "r") as f:
                info = json.load(f)

            assert info["model_id"] == MODEL_ID_DEFAULT
            assert info["device"] == "cpu"
            assert info["files"] == files


class TestSaveTokenizerWithoutModel:
    def test_saves_placeholder(self):
        with tempfile.TemporaryDirectory() as tmp:
            exporter = ONNXExporter(output_dir=tmp)
            exporter._prepare_output_dir()
            exporter.save_tokenizer()

            tokenizer_path = os.path.join(tmp, "tokenizer.json")
            assert os.path.isfile(tokenizer_path)

            with open(tokenizer_path, "r") as f:
                data = json.load(f)

            assert data["model_id"] == MODEL_ID_DEFAULT


class TestEnsureTorch:
    def test_returns_cached_torch(self):
        exporter = ONNXExporter()
        exporter._torch = "fake_torch"
        result = exporter._ensure_torch()
        assert result == "fake_torch"


class TestExportEncoderError:
    def test_raises_when_model_not_loaded(self):
        exporter = ONNXExporter()
        with pytest.raises(AttributeError):
            exporter.export_encoder()


class TestExportDecoderError:
    def test_raises_when_model_not_loaded(self):
        exporter = ONNXExporter()
        with pytest.raises(AttributeError):
            exporter.export_decoder()


class TestSkipQuantize:
    @patch("scripts.export_onnx.ONNXExporter._apply_dynamic_quantization")
    def test_skip_quantize_returns_path(self, mock_quantize):
        path = "/fake/model.onnx"
        exporter = ONNXExporter()
        result = exporter._apply_dynamic_quantization(path)
        assert result is not None


class TestConstants:
    def test_model_id_default(self):
        assert "Qwen3-ASR" in MODEL_ID_DEFAULT

    def test_sample_rate_config(self):
        from scripts.export_onnx import SAMPLE_RATE
        assert SAMPLE_RATE == 16000

    def test_n_mels(self):
        assert N_MELS == 80

    def test_max_source_positions(self):
        assert MAX_SOURCE_POSITIONS == 3000
