package pb

import (
	"bytes"
	"math"
	"testing"
)

func TestAudioChunkMarshalRoundtrip(t *testing.T) {
	original := &AudioChunk{
		AudioData: []byte{0x01, 0x02, 0x03, 0x04},
		Language:  "zh",
	}
	data := original.Marshal()

	var decoded AudioChunk
	if err := decoded.Unmarshal(data); err != nil {
		t.Fatalf("unmarshal failed: %v", err)
	}
	if !bytes.Equal(decoded.AudioData, original.AudioData) {
		t.Errorf("AudioData mismatch: got %v, want %v", decoded.AudioData, original.AudioData)
	}
	if decoded.Language != original.Language {
		t.Errorf("Language mismatch: got %s, want %s", decoded.Language, original.Language)
	}
}

func TestAudioChunkMarshalEmptyFields(t *testing.T) {
	original := &AudioChunk{}
	data := original.Marshal()
	if len(data) != 0 {
		t.Errorf("empty message should produce empty bytes, got %d bytes", len(data))
	}
}

func TestRecognitionResultMarshalRoundtrip(t *testing.T) {
	original := &RecognitionResult{
		Text:       "你好世界",
		IsFinal:    true,
		DurationMs: 1234.56,
	}
	data := original.Marshal()

	var decoded RecognitionResult
	if err := decoded.Unmarshal(data); err != nil {
		t.Fatalf("unmarshal failed: %v", err)
	}
	if decoded.Text != original.Text {
		t.Errorf("Text mismatch: got %s, want %s", decoded.Text, original.Text)
	}
	if decoded.IsFinal != original.IsFinal {
		t.Errorf("IsFinal mismatch: got %v, want %v", decoded.IsFinal, original.IsFinal)
	}
	if math.Abs(decoded.DurationMs-original.DurationMs) > 0.001 {
		t.Errorf("DurationMs mismatch: got %f, want %f", decoded.DurationMs, original.DurationMs)
	}
}

func TestRecognitionResultMarshalFalseBool(t *testing.T) {
	original := &RecognitionResult{
		Text:       "test",
		IsFinal:    false,
		DurationMs: 500.0,
	}
	data := original.Marshal()
	var decoded RecognitionResult
	decoded.Unmarshal(data)
	if decoded.IsFinal {
		t.Error("IsFinal should be false")
	}
}

func TestHealthRequestMarshal(t *testing.T) {
	req := &HealthRequest{}
	data := req.Marshal()
	if len(data) != 0 {
		t.Errorf("HealthRequest should marshal to 0 bytes, got %d", len(data))
	}
}

func TestHealthResponseMarshalRoundtrip(t *testing.T) {
	original := &HealthResponse{
		ModelLoaded: true,
		ModelName:   "Qwen/Qwen3-ASR-1.7B",
	}
	data := original.Marshal()

	var decoded HealthResponse
	if err := decoded.Unmarshal(data); err != nil {
		t.Fatalf("unmarshal failed: %v", err)
	}
	if decoded.ModelLoaded != original.ModelLoaded {
		t.Errorf("ModelLoaded mismatch: got %v, want %v", decoded.ModelLoaded, original.ModelLoaded)
	}
	if decoded.ModelName != original.ModelName {
		t.Errorf("ModelName mismatch: got %s, want %s", decoded.ModelName, original.ModelName)
	}
}

func TestHealthResponseMarshalFalseLoaded(t *testing.T) {
	original := &HealthResponse{
		ModelLoaded: false,
		ModelName:   "test-model",
	}
	data := original.Marshal()
	var decoded HealthResponse
	decoded.Unmarshal(data)
	if decoded.ModelLoaded {
		t.Error("ModelLoaded should be false")
	}
}

func TestEncodeDecodeMultipleChunks(t *testing.T) {
	chunks := []*AudioChunk{
		{AudioData: []byte{0xAA, 0xBB}, Language: "zh"},
		{AudioData: []byte{0xCC, 0xDD, 0xEE}, Language: "en"},
		{AudioData: []byte{}, Language: "ja"},
	}

	for i, ch := range chunks {
		data := ch.Marshal()
		var decoded AudioChunk
		if err := decoded.Unmarshal(data); err != nil {
			t.Fatalf("chunk %d unmarshal failed: %v", i, err)
		}
		if !bytes.Equal(decoded.AudioData, ch.AudioData) {
			t.Errorf("chunk %d AudioData mismatch", i)
		}
		if decoded.Language != ch.Language {
			t.Errorf("chunk %d Language mismatch", i)
		}
	}
}

func TestVarintEncoding(t *testing.T) {
	tests := []uint64{0, 1, 127, 128, 255, 256, 300, 10000, 1<<63 - 1}
	for _, v := range tests {
		data := appendVarint(nil, v)
		decoded, n := readVarint(data)
		if n <= 0 {
			t.Errorf("varint %d: readVarint returned %d", v, n)
			continue
		}
		if decoded != v {
			t.Errorf("varint %d: got %d", v, decoded)
		}
	}
}

func TestAppendString(t *testing.T) {
	s := "hello"
	buf := appendString(nil, s)
	expected := append(appendVarint(nil, 5), []byte(s)...)
	if !bytes.Equal(buf, expected) {
		t.Errorf("appendString mismatch")
	}
}
