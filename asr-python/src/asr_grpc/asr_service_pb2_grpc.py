"""Generated gRPC code for asr_service.proto"""

import grpc
from . import asr_service_pb2 as asr__service__pb2


class ASRServiceStub:
    def __init__(self, channel):
        self.StreamingRecognize = channel.stream_stream(
            '/asr.ASRService/StreamingRecognize',
            request_serializer=asr__service__pb2.AudioChunk.SerializeToString,
            response_deserializer=asr__service__pb2.RecognitionResult.FromString,
        )
        self.GetHealth = channel.unary_unary(
            '/asr.ASRService/GetHealth',
            request_serializer=asr__service__pb2.HealthRequest.SerializeToString,
            response_deserializer=asr__service__pb2.HealthResponse.FromString,
        )


class ASRServiceServicer:
    def StreamingRecognize(self, request_iterator, context):
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def GetHealth(self, request, context):
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')


def add_ASRServiceServicer_to_server(servicer, server):
    rpc_method_handlers = {
        'StreamingRecognize': grpc.stream_stream_rpc_method_handler(
            servicer.StreamingRecognize,
            request_deserializer=asr__service__pb2.AudioChunk.FromString,
            response_serializer=asr__service__pb2.RecognitionResult.SerializeToString,
        ),
        'GetHealth': grpc.unary_unary_rpc_method_handler(
            servicer.GetHealth,
            request_deserializer=asr__service__pb2.HealthRequest.FromString,
            response_serializer=asr__service__pb2.HealthResponse.SerializeToString,
        ),
    }
    generic_handler = grpc.method_handlers_generic_handler(
        'asr.ASRService',
        rpc_method_handlers,
    )
    server.add_generic_rpc_handlers((generic_handler,))
