package audio

import (
	"math"
	"testing"
)

func TestNewWienerFilterDefaults(t *testing.T) {
	w := NewWienerFilter(256)
	if w.fftSize != 256 {
		t.Errorf("expected fftSize 256, got %d", w.fftSize)
	}
	if w.hopSize != 128 {
		t.Errorf("expected hopSize 128, got %d", w.hopSize)
	}
	if w.noiseReady {
		t.Error("noise should not be ready initially")
	}
}

func TestWienerFilterSetSmoothing(t *testing.T) {
	w := NewWienerFilter(256)
	w.SetSmoothing(0.5)
	if w.smoothAlpha != 0.5 {
		t.Errorf("expected 0.5, got %f", w.smoothAlpha)
	}
}

func TestWienerFilterSetSmoothingClamp(t *testing.T) {
	w := NewWienerFilter(256)
	w.SetSmoothing(-1.0)
	if w.smoothAlpha != 0 {
		t.Errorf("expected 0, got %f", w.smoothAlpha)
	}
	w.SetSmoothing(2.0)
	if w.smoothAlpha != 1 {
		t.Errorf("expected 1, got %f", w.smoothAlpha)
	}
}

func TestWienerFilterProcessNoNoisePassThrough(t *testing.T) {
	w := NewWienerFilter(256)
	samples := generateInt16ToneAsSamples(400, 440, 16000, 16000)
	result := w.Process(samples)
	if len(result) < len(samples)/2 {
		t.Errorf("output too short: %d < %d", len(result), len(samples)/2)
	}
}

func TestWienerFilterEstimateNoise(t *testing.T) {
	w := NewWienerFilter(256)
	w.EstimateNoise(generateInt16Samples(400, 0))
	w.FinalizeNoiseEstimate()
	if !w.noiseReady {
		t.Error("noise should be ready after finalize")
	}
}

func TestWienerFilterProcessWithNoise(t *testing.T) {
	w := NewWienerFilter(256)

	w.EstimateNoise(generateInt16Samples(1000, 0))
	w.FinalizeNoiseEstimate()

	signal := generateInt16ToneAsSamples(400, 440, 16000, 16000)
	result := w.Process(signal)

	if len(result) == 0 {
		t.Error("process should produce output")
	}
	for _, v := range result {
		if v < -32768 || v > 32767 {
			t.Errorf("output out of range: %d", v)
		}
	}
}

func TestWienerFilterProcessStream(t *testing.T) {
	w := NewWienerFilter(256)

	w.EstimateNoise(generateInt16Samples(1000, 0))
	w.FinalizeNoiseEstimate()

	signal := generateInt16ToneAsSamples(300, 440, 16000, 16000)
	result := w.ProcessStream(signal)

	if len(result) == 0 {
		t.Error("processStream should produce output")
	}
}

func TestWienerFilterReset(t *testing.T) {
	w := NewWienerFilter(256)
	w.EstimateNoise(generateInt16Samples(500, 0))
	w.FinalizeNoiseEstimate()
	if !w.noiseReady {
		t.Fatal("noise should be ready")
	}

	w.Reset()
	if w.noiseReady {
		t.Error("reset should clear noiseReady")
	}
	if w.noiseFrames != 0 {
		t.Errorf("noiseFrames should be 0, got %d", w.noiseFrames)
	}
}

func TestWienerFilterProcessEmpty(t *testing.T) {
	w := NewWienerFilter(256)
	w.EstimateNoise(generateInt16Samples(500, 0))
	w.FinalizeNoiseEstimate()

	result := w.Process([]int16{})
	if len(result) != 0 {
		t.Errorf("empty input should produce empty output, got %d samples", len(result))
	}
}

func TestWienerFilterProcessStreamEmpty(t *testing.T) {
	w := NewWienerFilter(256)
	w.EstimateNoise(generateInt16Samples(500, 0))
	w.FinalizeNoiseEstimate()

	result := w.ProcessStream([]int16{})
	if len(result) != 0 {
		t.Error("empty input should produce empty output for stream")
	}
}

func TestWienerFilterProcessSmallInput(t *testing.T) {
	w := NewWienerFilter(256)
	w.EstimateNoise(generateInt16Samples(500, 0))
	w.FinalizeNoiseEstimate()

	result := w.Process(generateInt16Samples(10, 100))
	if len(result) == 0 {
		t.Error("small input should still process")
	}
}

func TestWienerFilterNoiseFloor(t *testing.T) {
	w := NewWienerFilter(256)
	if w.noiseFloor != 0.01 {
		t.Errorf("expected noiseFloor 0.01, got %f", w.noiseFloor)
	}
}

func generateInt16ToneAsSamples(n int, freq float64, amplitude float64, sampleRate int) []int16 {
	samples := make([]int16, n)
	for i := range samples {
		t := float64(i) / float64(sampleRate)
		val := amplitude * math.Sin(2*math.Pi*freq*t)
		if val > 32767 {
			val = 32767
		} else if val < -32768 {
			val = -32768
		}
		samples[i] = int16(val)
	}
	return samples
}
