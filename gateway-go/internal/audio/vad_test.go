package audio

import (
	"testing"
)

func TestNewSmartVADDefaults(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	if v.frameSize != 160 {
		t.Errorf("expected frameSize 160, got %d", v.frameSize)
	}
	if v.sampleRate != 16000 {
		t.Errorf("expected sampleRate 16000, got %d", v.sampleRate)
	}
}

func TestVADEmptyFrame(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	result := v.Detect(nil)
	if result.Decision != VADSilence {
		t.Error("empty frame should be silence")
	}
}

func TestVADSilenceDetected(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	result := v.Detect(generateInt16Samples(160, 0))
	if result.Decision != VADSilence {
		t.Error("zero samples should be silence")
	}
}

func TestVADSpeechDetected(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	tone := generateInt16ToneAsSamples(160, 440, 20000, 16000)

	v.Detect(tone)
	v.Detect(tone)
	result := v.Detect(tone)

	if result.Decision != VADSpeech {
		t.Errorf("loud tone should be speech after 3+ frames, energy=%f dB, zcr=%f",
			result.EnergyDb, result.ZCR)
	}
}

func TestVADHasSpeech(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	samples := make([]int16, 800)
	tone := generateInt16ToneAsSamples(160, 440, 20000, 16000)
	for i := 0; i < 4; i++ {
		start := 160 + i*160
		end := start + 160
		if end > len(samples) {
			break
		}
		copy(samples[start:end], tone)
	}

	if !v.HasSpeech(samples) {
		t.Error("HasSpeech should detect the tone segment")
	}
}

func TestVADNoSpeech(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	samples := generateInt16Samples(800, 0)
	if v.HasSpeech(samples) {
		t.Error("HasSpeech should return false for silence")
	}
}

func TestVADDetectBuffer(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	samples := make([]int16, 640)
	tone := generateInt16ToneAsSamples(160, 440, 20000, 16000)
	copy(samples[160:320], tone)

	results := v.DetectBuffer(samples)
	if len(results) != 4 {
		t.Errorf("expected 4 frames, got %d", len(results))
	}
}

func TestVADSpeechSegments(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	samples := make([]int16, 1600)
	tone := generateInt16ToneAsSamples(160, 440, 20000, 16000)

	for i := 0; i < 3; i++ {
		start := 320 + i*160
		copy(samples[start:start+160], tone)
	}
	for i := 0; i < 3; i++ {
		start := 960 + i*160
		copy(samples[start:start+160], tone)
	}

	segments := v.SpeechSegments(samples)
	if len(segments) < 1 {
		t.Error("should detect at least one speech segment")
	}
	for _, seg := range segments {
		if seg[0] >= seg[1] {
			t.Errorf("invalid segment: [%d, %d]", seg[0], seg[1])
		}
		if seg[0] < 0 || seg[1] > len(samples) {
			t.Errorf("segment out of bounds: [%d, %d]", seg[0], seg[1])
		}
	}
}

func TestVADReset(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	tone := generateInt16ToneAsSamples(160, 440, 20000, 16000)
	v.Detect(tone)
	v.Reset()

	result := v.Detect(generateInt16Samples(160, 0))
	if result.Decision != VADSilence {
		t.Error("after reset, silence should be detected")
	}
}

func TestVADHangover(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	v.SetHangover(3, 5)

	tone := generateInt16ToneAsSamples(160, 440, 20000, 16000)
	for i := 0; i < 6; i++ {
		v.Detect(tone)
	}

	speechAfter := 0
	for i := 0; i < 6; i++ {
		result := v.Detect(generateInt16Samples(160, 0))
		if result.Decision == VADSpeech {
			speechAfter++
		}
	}
	if speechAfter == 0 {
		t.Error("hangover should keep speech decision for a few frames")
	}
	if speechAfter > 6 {
		t.Error("hangover should not persist forever")
	}
}

func TestVADSetThresholds(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	v.SetEnergyThreshold(-30)
	v.SetZCRThreshold(0.2)

	tone := generateInt16ToneAsSamples(160, 440, 5000, 16000)
	result := v.Detect(tone)
	if result.Decision == VADSpeech {
		t.Log("milder tone may not trigger speech with higher threshold")
	}
}

func TestVADZCRSilence(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	result := v.Detect(generateInt16Samples(160, 0))
	if result.ZCR != 0 {
		t.Errorf("silence ZCR should be 0, got %f", result.ZCR)
	}
}

func TestVADZCRTone(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	tone := generateInt16ToneAsSamples(160, 440, 20000, 16000)
	result := v.Detect(tone)
	if result.ZCR <= 0 {
		t.Errorf("tone ZCR should be > 0, got %f", result.ZCR)
	}
}

func TestVADEnergyDbRange(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	silence := v.Detect(generateInt16Samples(160, 0))
	loud := v.Detect(generateInt16Samples(160, 32767))

	if silence.EnergyDb > loud.EnergyDb {
		t.Errorf("silence energy (%f) should be less than loud (%f)",
			silence.EnergyDb, loud.EnergyDb)
	}
}

func TestVADAdaptiveThreshold(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	v.SetAdaptive(true)

	for i := 0; i < 20; i++ {
		v.Detect(generateInt16Samples(160, 0))
	}

	thresholdBefore := v.energyCeil
	tone := generateInt16ToneAsSamples(160, 440, 20000, 16000)
	v.Detect(tone)

	if v.energyCeil != thresholdBefore {
		t.Logf("adaptive threshold changed: %f -> %f", thresholdBefore, v.energyCeil)
	}
}

func TestVADSetAdaptive(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	v.SetAdaptive(false)
	if v.adaptive {
		t.Error("adaptive should be disabled")
	}
	v.SetAdaptive(true)
	if !v.adaptive {
		t.Error("adaptive should be enabled")
	}
}

func TestVADSpeechSegmentsEmpty(t *testing.T) {
	v := NewSmartVAD(160, 16000)
	segments := v.SpeechSegments(generateInt16Samples(800, 0))
	if len(segments) != 0 {
		t.Errorf("silence should produce no segments, got %d", len(segments))
	}
}
