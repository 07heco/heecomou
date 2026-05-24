import io
import wave
import base64
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

os.environ["ASR_SKIP_LOAD"] = "true"

from fastapi.testclient import TestClient
from main import app


def create_silent_wav_b64(duration_sec: float = 1.0, sample_rate: int = 16000) -> str:
    n_samples = int(duration_sec * sample_rate)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(b"\x00\x00" * n_samples)
    return base64.b64encode(buf.getvalue()).decode()


@pytest.fixture
def client():
    return TestClient(app)


def test_health_check_no_model(client):
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["service"] == "asr-python"
    assert data["model_loaded"] is False


def test_health_response_format(client):
    response = client.get("/health")
    data = response.json()
    assert isinstance(data["status"], str)
    assert isinstance(data["service"], str)
    assert isinstance(data["model_loaded"], bool)


def test_recognize_missing_audio_field(client):
    response = client.post(
        "/api/v1/asr/recognize",
        json={"language": "zh"},
    )
    assert response.status_code == 422


def test_recognize_empty_request(client):
    response = client.post(
        "/api/v1/asr/recognize",
        json={},
    )
    assert response.status_code == 422


def test_recognize_invalid_language_type(client):
    response = client.post(
        "/api/v1/asr/recognize",
        json={"audio": "dGVzdA==", "language": 123},
    )
    assert response.status_code == 422


def test_openapi_docs_available(client):
    response = client.get("/openapi.json")
    assert response.status_code == 200
    data = response.json()
    assert "paths" in data
    assert "/api/v1/asr/recognize" in data["paths"]
    assert "/health" in data["paths"]


def test_docs_page_available(client):
    response = client.get("/docs")
    assert response.status_code == 200
