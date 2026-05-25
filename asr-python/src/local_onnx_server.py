import os
import sys
import logging
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import uvicorn

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("local_onnx_server")

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))

from src.inference.onnx_engine import WhisperOnnxEngine  # noqa: E402
from src.nlp.vocab_injector import VocabInjector  # noqa: E402

MODEL_DIR_DEFAULT = os.environ.get(
    "ONNX_MODEL_DIR",
    os.path.join(os.path.dirname(__file__), "..", "..", "onnx_models"),
)
BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8081")

app = FastAPI(title="HeecoMou Local ONNX ASR", version="1.0.0")

engine: WhisperOnnxEngine = None
vocab_injector: VocabInjector = None


class RecognizeRequest(BaseModel):
    audio_b64: str
    language: str = "zh"
    vocab_words: Optional[list[str]] = None
    user_id: Optional[int] = None


class RecognizeResponse(BaseModel):
    text: str
    duration_ms: float


@app.on_event("startup")
def startup():
    global engine, vocab_injector
    model_dir = os.environ.get("ONNX_MODEL_DIR", MODEL_DIR_DEFAULT)
    logger.info("Starting local ONNX ASR server, model_dir=%s", model_dir)
    engine = WhisperOnnxEngine(model_dir=model_dir)
    engine.load()
    vocab_injector = VocabInjector(backend_url=BACKEND_URL)
    logger.info("Server ready (vocab_injector backend=%s)", BACKEND_URL)


@app.on_event("shutdown")
def shutdown():
    global engine
    if engine is not None:
        engine.unload()
        engine = None


@app.get("/health")
def health():
    return {"status": "UP", "model_loaded": engine is not None and engine.is_loaded}


@app.post("/api/v1/asr/local/recognize", response_model=RecognizeResponse)
def recognize(req: RecognizeRequest):
    if engine is None or not engine.is_loaded:
        raise HTTPException(status_code=503, detail="Model not loaded")
    try:
        result = engine.recognize(req.audio_b64, req.language)
        text = result.text

        if (req.vocab_words or req.user_id is not None) and vocab_injector is not None:
            try:
                words = req.vocab_words or []
                text = vocab_injector.inject(
                    text, vocab_words=words, user_id=req.user_id
                )
                logger.info("Vocab injection applied, words=%d", len(words))
            except Exception:
                logger.exception("Vocab injection failed, using raw text")

        return RecognizeResponse(text=text, duration_ms=result.duration_ms)
    except Exception as e:
        logger.exception("Recognition failed")
        raise HTTPException(status_code=500, detail=str(e))


def main():
    port = int(os.environ.get("ONNX_PORT", "8085"))
    host = os.environ.get("ONNX_HOST", "127.0.0.1")
    uvicorn.run(app, host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
