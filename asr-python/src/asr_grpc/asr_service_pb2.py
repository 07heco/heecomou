"""Protocol buffer definitions for asr_service.proto"""

from google.protobuf import descriptor_pb2 as _descriptor_pb2
from google.protobuf import descriptor_pool as _descriptor_pool
from google.protobuf import message_factory as _message_factory
from google.protobuf import symbol_database as _symbol_database

_sym_db = _symbol_database.Default()

_FDPB = _descriptor_pb2.FieldDescriptorProto


def _add_field(message, name, number, field_type):
    f = message.field.add()
    f.name = name
    f.number = number
    f.type = field_type
    return f


def _build_file_descriptor():
    fdp = _descriptor_pb2.FileDescriptorProto()
    fdp.name = "asr_service.proto"
    fdp.package = "asr"
    fdp.syntax = "proto3"

    audio_chunk = fdp.message_type.add()
    audio_chunk.name = "AudioChunk"
    _add_field(audio_chunk, "audio_data", 1, _FDPB.TYPE_BYTES)
    _add_field(audio_chunk, "language", 2, _FDPB.TYPE_STRING)

    rec_result = fdp.message_type.add()
    rec_result.name = "RecognitionResult"
    _add_field(rec_result, "text", 1, _FDPB.TYPE_STRING)
    _add_field(rec_result, "is_final", 2, _FDPB.TYPE_BOOL)
    _add_field(rec_result, "duration_ms", 3, _FDPB.TYPE_DOUBLE)

    health_req = fdp.message_type.add()
    health_req.name = "HealthRequest"

    health_res = fdp.message_type.add()
    health_res.name = "HealthResponse"
    _add_field(health_res, "model_loaded", 1, _FDPB.TYPE_BOOL)
    _add_field(health_res, "model_name", 2, _FDPB.TYPE_STRING)

    svc = fdp.service.add()
    svc.name = "ASRService"
    m = svc.method.add()
    m.name = "StreamingRecognize"
    m.input_type = ".asr.AudioChunk"
    m.output_type = ".asr.RecognitionResult"
    m.client_streaming = True
    m.server_streaming = True
    m = svc.method.add()
    m.name = "GetHealth"
    m.input_type = ".asr.HealthRequest"
    m.output_type = ".asr.HealthResponse"

    return fdp.SerializeToString()


_pool = _descriptor_pool.Default()
_file_desc = _pool.AddSerializedFile(_build_file_descriptor())


def _make_msg(name):
    return _message_factory.GetMessageClass(
        _file_desc.message_types_by_name[name]
    )


AudioChunk = _make_msg("AudioChunk")
RecognitionResult = _make_msg("RecognitionResult")
HealthRequest = _make_msg("HealthRequest")
HealthResponse = _make_msg("HealthResponse")
