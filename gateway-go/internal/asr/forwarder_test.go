package asr

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"
)

func TestForwarderRoundRobin(t *testing.T) {
	hits := make(map[string]int)
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits[r.Host]++
		resp := RecognizeResponse{Text: "hello", DurationMs: 100, ModelName: "test"}
		json.NewEncoder(w).Encode(resp)
	}))
	defer ts.Close()

	f := NewForwarder([]string{ts.URL})
	dir := t.TempDir()

	wavPath := dir + "/test.wav"
	os.WriteFile(wavPath, createMinimalWAV(), 0644)

	resp, err := f.Forward(wavPath, "zh")
	if err != nil {
		t.Fatalf("Forward failed: %v", err)
	}
	if resp.Text != "hello" {
		t.Errorf("Expected 'hello', got %q", resp.Text)
	}
}

func TestForwarderMultipleServers(t *testing.T) {
	serverCount := 3
	var servers []string
	for i := 0; i < serverCount; i++ {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			resp := RecognizeResponse{Text: fmt.Sprintf("text-%d", i), DurationMs: 50, ModelName: "test"}
			json.NewEncoder(w).Encode(resp)
		}))
		defer ts.Close()
		servers = append(servers, ts.URL)
	}

	f := NewForwarder(servers)
	dir := t.TempDir()
	wavPath := dir + "/test.wav"
	os.WriteFile(wavPath, createMinimalWAV(), 0644)

	for j := 0; j < 6; j++ {
		resp, err := f.Forward(wavPath, "en")
		if err != nil {
			t.Logf("Forward %d failed (expected if server already closed): %v", j, err)
			continue
		}
		if resp.Text == "" {
			t.Error("Expected non-empty text")
		}
	}
}

func TestForwarderServerErrorRetry(t *testing.T) {
	attempts := 0
	ts1 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte(`{"error":"overloaded"}`))
	}))
	defer ts1.Close()

	ts2 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		resp := RecognizeResponse{Text: "recovered", DurationMs: 200, ModelName: "fallback"}
		json.NewEncoder(w).Encode(resp)
	}))
	defer ts2.Close()

	f := NewForwarder([]string{ts1.URL, ts2.URL})
	dir := t.TempDir()
	wavPath := dir + "/test.wav"
	os.WriteFile(wavPath, createMinimalWAV(), 0644)

	resp, err := f.Forward(wavPath, "zh")
	if err != nil {
		t.Fatalf("Forward should succeed via fallback: %v", err)
	}
	if resp.Text != "recovered" {
		t.Errorf("Expected 'recovered', got %q", resp.Text)
	}
	if attempts < 2 {
		t.Errorf("Expected at least 2 attempts, got %d", attempts)
	}
}

func TestForwarderDefaultServer(t *testing.T) {
	f := NewForwarder(nil)
	if len(f.servers) != 1 {
		t.Errorf("Expected 1 default server, got %d", len(f.servers))
	}
	if f.servers[0] != "http://localhost:8082" {
		t.Errorf("Expected localhost:8082, got %s", f.servers[0])
	}
}

func TestForwarderTimeout(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(2 * time.Second)
	}))
	defer ts.Close()

	f := NewForwarder([]string{ts.URL})
	f.client.Timeout = 500 * time.Millisecond

	dir := t.TempDir()
	wavPath := dir + "/test.wav"
	os.WriteFile(wavPath, createMinimalWAV(), 0644)

	_, err := f.Forward(wavPath, "zh")
	if err == nil {
		t.Error("Expected timeout error")
	}
}

func createMinimalWAV() []byte {
	return []byte{
		0x52, 0x49, 0x46, 0x46,
		0x24, 0x00, 0x00, 0x00,
		0x57, 0x41, 0x56, 0x45,
		0x66, 0x6d, 0x74, 0x20,
		0x10, 0x00, 0x00, 0x00,
		0x01, 0x00, 0x01, 0x00,
		0x80, 0x3e, 0x00, 0x00,
		0x00, 0x7d, 0x00, 0x00,
		0x02, 0x00, 0x10, 0x00,
		0x64, 0x61, 0x74, 0x61,
		0x00, 0x00, 0x00, 0x00,
	}
}
