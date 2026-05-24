package audio

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
)

func TestSessionWriterAppendSamples(t *testing.T) {
	dir := t.TempDir()
	w := NewSessionWriter(AudioFormat{
		SampleRate:  16000,
		NumChannels: 1,
		BitDepth:    16,
	}, dir)

	data := make([]byte, 160)
	for i := 0; i < 80; i++ {
		data[i*2] = byte(i)
		data[i*2+1] = 0
	}

	n, err := w.AppendSamples(data)
	if err != nil {
		t.Fatalf("AppendSamples error: %v", err)
	}
	if n != 80 {
		t.Errorf("Expected 80 samples, got %d", n)
	}
	if w.SampleCount() != 80 {
		t.Errorf("Expected 80 total, got %d", w.SampleCount())
	}
}

func TestSessionWriterDuration(t *testing.T) {
	dir := t.TempDir()
	w := NewSessionWriter(AudioFormat{
		SampleRate:  16000,
		NumChannels: 1,
		BitDepth:    16,
	}, dir)

	samplesPerSecond := 16000
	data := make([]byte, samplesPerSecond*2)

	for i := 0; i < samplesPerSecond; i++ {
		data[i*2] = 0
		data[i*2+1] = 0
	}

	_, _ = w.AppendSamples(data)

	expected := 1.0
	if w.Duration() != expected {
		t.Errorf("Expected %f, got %f", expected, w.Duration())
	}
}

func TestSessionWriterFinalizeCreatesFiles(t *testing.T) {
	dir := t.TempDir()
	w := NewSessionWriter(AudioFormat{
		SampleRate:  16000,
		NumChannels: 1,
		BitDepth:    16,
	}, dir)

	data := make([]byte, 320)
	_, _ = w.AppendSamples(data)

	if err := w.Finalize(); err != nil {
		t.Fatalf("Finalize error: %v", err)
	}

	wavPath := filepath.Join(dir, "audio.wav")
	if _, err := os.Stat(wavPath); os.IsNotExist(err) {
		t.Error("audio.wav not created")
	}

	metaPath := filepath.Join(dir, "metadata.json")
	if _, err := os.Stat(metaPath); os.IsNotExist(err) {
		t.Error("metadata.json not created")
	}

	wavData, _ := os.ReadFile(wavPath)
	if !bytes.Contains(wavData, []byte("RIFF")) {
		t.Error("WAV file missing RIFF header")
	}
	if !bytes.Contains(wavData, []byte("WAVE")) {
		t.Error("WAV file missing WAVE marker")
	}
}

func TestSessionWriterEmptyFinalize(t *testing.T) {
	dir := t.TempDir()
	w := NewSessionWriter(AudioFormat{
		SampleRate:  16000,
		NumChannels: 1,
		BitDepth:    16,
	}, dir)

	if err := w.Finalize(); err != nil {
		t.Fatalf("Finalize on empty should not error: %v", err)
	}
}

func TestDirReturnsCorrectPath(t *testing.T) {
	w := NewSessionWriter(AudioFormat{}, "/tmp/test")
	if w.Dir() != "/tmp/test" {
		t.Errorf("Expected /tmp/test, got %s", w.Dir())
	}
}
