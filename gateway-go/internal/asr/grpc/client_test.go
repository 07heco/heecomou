package grpc

import (
	"bytes"
	"testing"
	"time"
)

func TestMakeGRPCFrame(t *testing.T) {
	payload := []byte{0x01, 0x02, 0x03}
	frame := makeGRPCFrame(payload)
	if len(frame) != 5+len(payload) {
		t.Errorf("expected %d bytes, got %d", 5+len(payload), len(frame))
	}
	if frame[0] != 0 {
		t.Errorf("compression flag should be 0")
	}
	if frame[1] != 0 || frame[2] != 0 || frame[3] != 0 || frame[4] != 3 {
		t.Errorf("length bytes mismatch")
	}
	if !bytes.Equal(frame[5:], payload) {
		t.Errorf("payload mismatch")
	}
}

func TestParseGRPCResponse(t *testing.T) {
	payload := []byte{0x0A, 0x0B, 0x0C}
	full := makeGRPCFrame(payload)
	result, err := parseGRPCResponse(full)
	if err != nil {
		t.Fatalf("parse failed: %v", err)
	}
	if !bytes.Equal(result, payload) {
		t.Errorf("payload mismatch: got %v, want %v", result, payload)
	}
}

func TestParseGRPCResponseTooShort(t *testing.T) {
	_, err := parseGRPCResponse([]byte{0x00, 0x00})
	if err == nil {
		t.Error("expected error for short response")
	}
}

func TestParseGRPCResponseTruncated(t *testing.T) {
	full := makeGRPCFrame([]byte{0x01, 0x02, 0x03, 0x04})
	truncated := full[:6]
	_, err := parseGRPCResponse(truncated)
	if err == nil {
		t.Error("expected error for truncated response")
	}
}

func TestFormatGRPCTimeout(t *testing.T) {
	tests := []struct {
		d        time.Duration
		expected string
	}{
		{0, "1S"},
		{500 * time.Millisecond, "500m"},
		{1 * time.Second, "1S"},
		{5 * time.Second, "5S"},
		{30 * time.Second, "30S"},
	}
	for _, tt := range tests {
		result := formatGRPCTimeout(tt.d)
		if result != tt.expected {
			t.Errorf("formatGRPCTimeout(%v): got %s, want %s", tt.d, result, tt.expected)
		}
	}
}

func TestEncodeHPACKHeader(t *testing.T) {
	data := encodeHPACKHeader("content-type", "application/grpc+proto")
	if len(data) < 3 {
		t.Fatal("header too short")
	}
	if data[0] != 0x40 {
		t.Errorf("expected 0x40 prefix, got 0x%02x", data[0])
	}
}

func TestMakeFrame(t *testing.T) {
	payload := []byte{0x01, 0x02}
	frameType := uint8(0)
	flags := uint8(0x4)
	streamID := uint32(1)

	frame := makeFrame(frameType, flags, streamID, payload)
	if frame[3] != frameType {
		t.Errorf("frame type mismatch")
	}
	if frame[4] != flags {
		t.Errorf("flags mismatch")
	}
	streamIDGot := uint32(frame[5])<<24 | uint32(frame[6])<<16 | uint32(frame[7])<<8 | uint32(frame[8])
	if streamIDGot != streamID {
		t.Errorf("stream ID mismatch: got %d, want %d", streamIDGot, streamID)
	}
	if !bytes.Equal(frame[9:], payload) {
		t.Errorf("payload mismatch")
	}
}

func TestEncodeHeadersContainsRequiredFields(t *testing.T) {
	data := encodeHeaders(1, "/test.Service/Method", "application/grpc+proto", 10*time.Second)
	if len(data) < 9+30 {
		t.Errorf("headers too short: %d bytes", len(data))
	}
	if data[3] != frameHeaders {
		t.Errorf("expected HEADERS frame type")
	}
	if data[4]&flagEndHeaders == 0 {
		t.Errorf("expected END_HEADERS flag")
	}
}
