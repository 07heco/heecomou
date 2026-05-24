package audio

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

type AudioFormat struct {
	SampleRate  int
	NumChannels int
	BitDepth    int
}

type SessionWriter struct {
	Format     AudioFormat
	dir        string
	rawSamples []int16
	startTime  time.Time
}

func NewSessionWriter(format AudioFormat, dir string) *SessionWriter {
	return &SessionWriter{
		Format:    format,
		dir:       dir,
		startTime: time.Now(),
	}
}

func (sw *SessionWriter) Dir() string {
	return sw.dir
}

func (sw *SessionWriter) AppendSamples(data []byte) (int, error) {
	n := len(data) / 2
	for i := 0; i < len(data)-1; i += 2 {
		sample := int16(binary.LittleEndian.Uint16(data[i : i+2]))
		sw.rawSamples = append(sw.rawSamples, sample)
	}
	return n, nil
}

func (sw *SessionWriter) SampleCount() int {
	return len(sw.rawSamples)
}

func (sw *SessionWriter) Duration() float64 {
	return float64(len(sw.rawSamples)) / float64(sw.Format.SampleRate*sw.Format.NumChannels)
}

func (sw *SessionWriter) Finalize() error {
	if err := sw.writeMetadata(); err != nil {
		return fmt.Errorf("write metadata: %w", err)
	}
	if err := sw.writeWav(); err != nil {
		return fmt.Errorf("write wav: %w", err)
	}
	return nil
}

func (sw *SessionWriter) writeWav() error {
	if len(sw.rawSamples) == 0 {
		return nil
	}

	path := filepath.Join(sw.dir, "audio.wav")
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	defer f.Close()

	sampleRate := uint32(sw.Format.SampleRate)
	numChannels := uint16(sw.Format.NumChannels)
	bitsPerSample := uint16(sw.Format.BitDepth)
	byteRate := sampleRate * uint32(numChannels) * uint32(bitsPerSample/8)
	blockAlign := numChannels * bitsPerSample / 8
	dataSize := uint32(len(sw.rawSamples) * 2)

	writeU32 := func(v uint32) { binary.Write(f, binary.LittleEndian, v) }
	writeU16 := func(v uint16) { binary.Write(f, binary.LittleEndian, v) }

	f.Write([]byte("RIFF"))
	writeU32(36 + dataSize)
	f.Write([]byte("WAVE"))

	f.Write([]byte("fmt "))
	writeU32(16)
	writeU16(1)
	writeU16(numChannels)
	writeU32(sampleRate)
	writeU32(byteRate)
	writeU16(blockAlign)
	writeU16(bitsPerSample)

	f.Write([]byte("data"))
	writeU32(dataSize)

	for _, s := range sw.rawSamples {
		binary.Write(f, binary.LittleEndian, s)
	}
	return nil
}

func (sw *SessionWriter) writeMetadata() error {
	meta := map[string]interface{}{
		"sample_rate":     sw.Format.SampleRate,
		"num_channels":    sw.Format.NumChannels,
		"bit_depth":       sw.Format.BitDepth,
		"sample_count":    len(sw.rawSamples),
		"duration_sec":    sw.Duration(),
		"start_timestamp": sw.startTime.UTC().Format(time.RFC3339),
	}

	data, err := json.MarshalIndent(meta, "", "  ")
	if err != nil {
		return err
	}
	path := filepath.Join(sw.dir, "metadata.json")
	return os.WriteFile(path, data, 0644)
}
