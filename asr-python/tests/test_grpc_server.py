import io
import os
import sys
import wave

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

os.environ["ASR_SKIP_LOAD"] = "true"

from inference.engine import ASREngine
from asr_grpc.asr_service_pb2 import HealthRequest, HealthResponse
from grpc_server import ASRServicer, create_server


@pytest.fixture
def engine():
    return ASREngine()


@pytest.fixture
def servicer(engine):
    return ASRServicer(engine)


def _generate_pcm_samples(duration_ms: int = 100, sample_rate: int = 16000) -> bytes:
    import math
    n_samples = int(duration_ms * sample_rate / 1000)
    data = bytearray()
    for i in range(n_samples):
        t = i / sample_rate
        val = int(16000 * math.sin(2 * math.pi * 440 * t))
        data.extend(val.to_bytes(2, 'little', signed=True))
    return bytes(data)


class TestASRServicer:
    def test_get_health_returns_model_loaded(self, engine, servicer):
        response = servicer.GetHealth(HealthRequest(), None)
        assert isinstance(response, HealthResponse)
        assert response.model_loaded == engine.is_loaded
        assert response.model_name == engine.model_id

    def test_get_health_model_not_loaded_initially(self, engine, servicer):
        response = servicer.GetHealth(HealthRequest(), None)
        assert response.model_loaded is False

    def test_pcm_to_wav_produces_valid_wav(self, servicer):
        pcm = _generate_pcm_samples(duration_ms=100)
        wav_bytes = servicer._pcm_to_wav(pcm)
        buf = io.BytesIO(wav_bytes)
        with wave.open(buf, "rb") as wf:
            assert wf.getnchannels() == 1
            assert wf.getsampwidth() == 2
            assert wf.getframerate() == 16000
            assert wf.getnframes() > 0

    def test_pcm_to_wav_empty_data(self, servicer):
        wav_bytes = servicer._pcm_to_wav(b"")
        buf = io.BytesIO(wav_bytes)
        with wave.open(buf, "rb") as wf:
            assert wf.getnchannels() == 1
            assert wf.getnframes() == 0

    def test_pcm_to_wav_custom_params(self, servicer):
        pcm = _generate_pcm_samples(duration_ms=50, sample_rate=8000)
        wav_bytes = servicer._pcm_to_wav(pcm, sample_rate=8000, num_channels=2, sample_width=1)
        buf = io.BytesIO(wav_bytes)
        with wave.open(buf, "rb") as wf:
            assert wf.getnchannels() == 2
            assert wf.getsampwidth() == 1
            assert wf.getframerate() == 8000


class TestCreateServer:
    def test_create_server_returns_grpc_server(self, engine):
        server = create_server(engine, port=50054)
        assert server is not None
        server.stop(0)

    def test_create_server_custom_port(self, engine):
        server = create_server(engine, port=50055, max_workers=5)
        assert server is not None
        server.stop(0)
