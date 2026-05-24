import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from inference.engine import ASREngine

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

model_id = os.getenv("ASR_MODEL_ID", "Qwen/Qwen3-ASR-1.7B")
engine = ASREngine(model_id=model_id)


@asynccontextmanager
async def lifespan(app: FastAPI):
    skip_load = os.getenv("ASR_SKIP_LOAD", "").lower() in ("1", "true", "yes")
    if skip_load:
        logger.info("Skipping model load (ASR_SKIP_LOAD=true)")
    else:
        try:
            engine.load()
        except Exception as e:
            logger.error("Failed to load model on startup: %s", e)
            logger.info("Model will be loaded on first request")
    yield
    try:
        engine.unload()
    except Exception:
        pass


app = FastAPI(
    title="HeecoMou ASR Service",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class RecognizeRequest(BaseModel):
    audio: str = Field(..., description="Base64-encoded WAV audio data")
    language: str = Field(default="zh", description="Language code (zh/en/...)")


class RecognizeResponse(BaseModel):
    text: str
    duration_ms: float
    model_name: str


class HealthResponse(BaseModel):
    status: str
    service: str
    model_loaded: bool


@app.get("/health")
async def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        service="asr-python",
        model_loaded=engine.is_loaded,
    )


@app.post("/api/v1/asr/recognize", response_model=RecognizeResponse)
async def recognize(req: RecognizeRequest):
    try:
        if not engine.is_loaded:
            engine.load()
    except Exception as e:
        raise HTTPException(
            status_code=503,
            detail=f"Model failed to load: {str(e)}",
        )

    try:
        result = engine.recognize(req.audio, language=req.language)
        return RecognizeResponse(
            text=result.text,
            duration_ms=result.duration_ms,
            model_name=result.model_name,
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.exception("Recognition failed")
        raise HTTPException(status_code=500, detail=f"Recognition error: {str(e)}")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8082)
