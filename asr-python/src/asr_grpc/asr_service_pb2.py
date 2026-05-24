"""Protocol buffer definitions for asr_service.proto"""

from google.protobuf import descriptor_pb2 as _descriptor_pb2
from google.protobuf import descriptor_pool as _descriptor_pool
from google.protobuf import message_factory as _message_factory
from google.protobuf import symbol_database as _symbol_database

_sym_db = _symbol_database.Default()

def _build_file_descriptor():
    fdp = _descriptor_pb2.FileDescriptorProto()
    fdp.name = "asr_service.proto"
    fdp.package = "asr"
    fdp.syntax = "proto3"

    audio_chunk = fdp.message_type.add()
    audio_chunk.name = "AudioChunk"
    f = audio_chunk.field.add()
    f.name = "audio_data"; f.number = 1; f.type = _descriptor_pb2.FieldDescriptorProto.TYPE_BYTES
    f = audio_chunk.field.add()
    f.name = "language"; f.number = 2; f.type = _descriptor_pb2.FieldDescriptorProto.TYPE_STRING

    rec_result = fdp.message_type.add()
    rec_result.name = "RecognitionResult"
    f = rec_result.field.add()
    f.name = "text"; f.number = 1; f.type = _descriptor_pb2.FieldDescriptorProto.TYPE_STRING
    f = rec_result.field.add()
    f.name = "is_final"; f.number = 2; f.type = _descriptor_pb2.FieldDescriptorProto.TYPE_BOOL
    f = rec_result.field.add()
    f.name = "duration_ms"; f.number = 3; f.type = _descriptor_pb2.FieldDescriptorProto.TYPE_DOUBLE

    health_req = fdp.message_type.add()
    health_req.name = "HealthRequest"

    health_res = fdp.message_type.add()
    health_res.name = "HealthResponse"
    f = health_res.field.add()
    f.name = "model_loaded"; f.number = 1; f.type = _descriptor_pb2.FieldDescriptorProto.TYPE_BOOL
    f = health_res.field.add()
    f.name = "model_name"; f.number = 2; f.type = _descriptor_pb2.FieldDescriptorProto.TYPE_STRING

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
    cls = _message_factory.GetMessageClass(_file_desc.message_types_by_name[name])
    return cls

AudioChunk = _make_msg("AudioChunk")
RecognitionResult = _make_msg("RecognitionResult")
HealthRequest = _make_msg("HealthRequest")
HealthResponse = _make_msg("HealthResponse")
