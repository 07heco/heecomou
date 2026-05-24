import io
import wave
import base64
import logging
import grpc

from inference.engine import ASREngine
from asr_grpc.asr_service_pb2 import (
    AudioChunk,
    RecognitionResult,
    HealthRequest,
    HealthResponse,
)
from asr_grpc.asr_service_pb2_grpc import (
    ASRServiceServicer,
    add_ASRServiceServicer_to_server,
)

logger = logging.getLogger(__name__)


class ASRServicer(ASRServiceServicer):
    def __init__(self, engine: ASREngine):
        self.engine = engine

    def StreamingRecognize(self, request_iterator, context):
        pcm_buffer = bytearray()
        language = "zh"

        try:
            for chunk in request_iterator:
                pcm_buffer.extend(chunk.audio_data)
                if chunk.language:
                    language = chunk.language

            if len(pcm_buffer) == 0:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("No audio data received")
                return

            wav_bytes = self._pcm_to_wav(bytes(pcm_buffer))
            audio_b64 = base64.b64encode(wav_bytes).decode()

            if not self.engine.is_loaded:
                self.engine.load()

            result = self.engine.recognize(audio_b64, language=language)

            yield RecognitionResult(
                text=result.text,
                is_final=True,
                duration_ms=result.duration_ms,
            )
        except Exception as e:
            logger.exception("Streaming recognition failed")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))

    def GetHealth(self, request, context):
        return HealthResponse(
            model_loaded=self.engine.is_loaded,
            model_name=self.engine.model_id,
        )

    @staticmethod
    def _pcm_to_wav(
        pcm_data: bytes,
        sample_rate: int = 16000,
        num_channels: int = 1,
        sample_width: int = 2,
    ) -> bytes:
        buf = io.BytesIO()
        with wave.open(buf, "wb") as wf:
            wf.setnchannels(num_channels)
            wf.setsampwidth(sample_width)
            wf.setframerate(sample_rate)
            wf.writeframes(pcm_data)
        return buf.getvalue()


def create_server(engine: ASREngine, port: int = 50051, max_workers: int = 10):
    server = grpc.server(
        thread_pool=None,
        maximum_concurrent_rpcs=max_workers,
    )
    add_ASRServiceServicer_to_server(ASRServicer(engine), server)
    server.add_insecure_port(f"0.0.0.0:{port}")
    return server
