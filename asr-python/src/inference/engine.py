import io
import wave
import base64
import logging
import time
from dataclasses import dataclass
from typing import Optional

import numpy as np
import zhconv

logger = logging.getLogger(__name__)


@dataclass
class RecognitionResult:
    text: str
    duration_ms: float
    model_name: str

    def to_dict(self):
        return {
            "text": self.text,
            "duration_ms": self.duration_ms,
            "model_name": self.model_name,
        }


class ASREngine:
    MODEL_ID = "openai/whisper-small"

    def __init__(self, model_id: Optional[str] = None, device: Optional[str] = None):
        self.model_id = model_id or self.MODEL_ID
        self._model = None
        self._processor = None
        self._loaded = False
        self._torch = None

        if device is None:
            self._device = "cpu"
        else:
            self._device = device

    def _ensure_torch(self):
        if self._torch is None:
            import torch
            if self._device.startswith("cuda") and not torch.cuda.is_available():
                self._device = "cpu"
            self._torch = torch
        return self._torch

    @property
    def is_loaded(self) -> bool:
        return self._loaded

    def load(self):
        if self._loaded:
            return

        torch = self._ensure_torch()

        logger.info("Loading ASR model %s on %s...", self.model_id, self._device)
        start = time.time()

        from transformers import AutoModelForSpeechSeq2Seq, AutoProcessor

        self._processor = AutoProcessor.from_pretrained(self.model_id)

        torch_dtype = (
            torch.float16
            if self._device.startswith("cuda")
            else torch.float32
        )
        self._model = AutoModelForSpeechSeq2Seq.from_pretrained(
            self.model_id,
            torch_dtype=torch_dtype,
            low_cpu_mem_usage=True,
        ).to(self._device)

        self._model.eval()

        elapsed = time.time() - start
        self._loaded = True
        logger.info("Model loaded in %.1fs on %s", elapsed, self._device)

    def unload(self):
        if self._model is not None:
            del self._model
            self._model = None
        if self._processor is not None:
            del self._processor
            self._processor = None
        self._loaded = False
        if self._torch is not None and self._torch.cuda.is_available():
            self._torch.cuda.empty_cache()

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

    def recognize(self, audio_b64: str, language: str = "zh") -> RecognitionResult:
        if not self._loaded:
            raise RuntimeError("Model not loaded. Call load() first.")

        torch = self._ensure_torch()
        audio_np, sample_rate = self._decode_audio(audio_b64)

        start_time = time.time()

        input_features = self._processor(
            audio_np,
            sampling_rate=sample_rate,
            return_tensors="pt",
        ).input_features.to(self._device)

        predicted_ids = self._model.generate(
            input_features,
            language=language,
            task="transcribe",
        )

        text = self._processor.batch_decode(
            predicted_ids, skip_special_tokens=True
        )[0].strip()

        # Convert to Simplified Chinese
        text = zhconv.convert(text, "zh-cn")

        duration_ms = (time.time() - start_time) * 1000.0

        logger.info("Recognition completed in %.0fms: %s", duration_ms, text[:50])
        return RecognitionResult(
            text=text,
            duration_ms=duration_ms,
            model_name=self.model_id,
        )
