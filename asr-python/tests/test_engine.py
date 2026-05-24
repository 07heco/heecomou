import io
import wave
import struct
import base64
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

from inference.engine import ASREngine


def create_silent_wav_b64(duration_sec: float = 1.0, sample_rate: int = 16000) -> str:
    n_samples = int(duration_sec * sample_rate)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(b"\x00\x00" * n_samples)
    return base64.b64encode(buf.getvalue()).decode()


def create_tone_wav_b64(
    frequency: float = 440.0,
    duration_sec: float = 1.0,
    sample_rate: int = 16000,
) -> str:
    import math
    n_samples = int(duration_sec * sample_rate)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        for i in range(n_samples):
            t = i / sample_rate
            val = int(16000 * math.sin(2 * math.pi * frequency * t))
            wf.writeframes(struct.pack("<h", val))
    return base64.b64encode(buf.getvalue()).decode()


class TestASREngineLifecycle:
    def test_engine_initial_state(self):
        engine = ASREngine()
        assert not engine.is_loaded

    def test_decode_audio_returns_valid_array(self):
        engine = ASREngine()
        b64 = create_silent_wav_b64(duration_sec=0.5)
        audio, sr = engine._decode_audio(b64)
        assert sr == 16000
        assert len(audio) == 8000
        assert audio.dtype.name == "float32"

    def test_decode_tone_audio_length(self):
        engine = ASREngine()
        b64 = create_tone_wav_b64(duration_sec=0.2)
        audio, sr = engine._decode_audio(b64)
        assert sr == 16000
        assert abs(len(audio) - 3200) < 100

    def test_decode_audio_base64_format(self):
        engine = ASREngine()
        b64 = create_silent_wav_b64(duration_sec=0.1)
        assert len(b64) > 0
        audio, sr = engine._decode_audio(b64)
        assert sr == 16000
        assert len(audio) == 1600

    def test_engine_device_selection(self):
        engine = ASREngine(device="cpu")
        assert engine._device == "cpu"

        engine2 = ASREngine(device="cuda:1")
        assert engine2._device == "cuda:1"

    def test_engine_model_id_default(self):
        engine = ASREngine()
        assert engine.model_id == "Qwen/Qwen3-ASR-1.7B"

    def test_engine_model_id_custom(self):
        engine = ASREngine(model_id="Qwen/Qwen3-ASR-0.6B")
        assert engine.model_id == "Qwen/Qwen3-ASR-0.6B"

    def test_engine_not_loaded_initially(self):
        engine = ASREngine()
        assert engine.is_loaded is False
