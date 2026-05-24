package audio

import (
	"math"
	"testing"
)

func generateInt16Samples(count int, amplitude int16) []int16 {
	samples := make([]int16, count)
	for i := range samples {
		samples[i] = amplitude
	}
	return samples
}

func generateInt16Tone(count int, frequency, amplitude float64, sampleRate int) []int16 {
	samples := make([]int16, count)
	for i := range samples {
		t := float64(i) / float64(sampleRate)
		val := amplitude * math.Sin(2*math.Pi*frequency*t)
		samples[i] = int16(val)
	}
	return samples
}

func TestNewNoiseEstimatorDefaults(t *testing.T) {
	e := NewNoiseEstimator(16000)
	if e.sampleRate != 16000 {
		t.Errorf("expected sample rate 16000, got %d", e.sampleRate)
	}
	if e.initialized {
		t.Error("estimator should not be initialized")
	}
	if e.CurrentDb() != -96.0 {
		t.Errorf("expected initial db -96.0, got %f", e.CurrentDb())
	}
}

func TestNoiseEstimatorSilence(t *testing.T) {
	e := NewNoiseEstimator(16000)
	samples := generateInt16Samples(160, 0)
	db := e.EstimateDb(samples)
	if db > -80 {
		t.Errorf("silence should be very quiet, got %f dB", db)
	}
	level := e.CurrentLevel()
	if level != NoiseQuiet {
		t.Errorf("silence should be quiet, got %s", level)
	}
}

func TestNoiseEstimatorLoudSignal(t *testing.T) {
	e := NewNoiseEstimator(16000)
	e.SetThresholds(-45, -25)
	samples := generateInt16Samples(160, 30000)
	db := e.EstimateDb(samples)
	if db < -15 {
		t.Errorf("loud signal should be above -15dB, got %f", db)
	}
	level := e.CurrentLevel()
	if level != NoiseLoud {
		t.Errorf("loud signal should be loud, got %s (db=%f)", level, db)
	}
}

func TestNoiseEstimatorNormalSignal(t *testing.T) {
	e := NewNoiseEstimator(16000)
	e.SetThresholds(-45, -25)
	samples := generateInt16Samples(160, 800)
	db := e.EstimateDb(samples)
	level := e.CurrentLevel()
	if level != NoiseNormal {
		t.Errorf("moderate signal should be normal, got %s (db=%f)", level, db)
	}
}

func TestNoiseEstimatorEmptyInput(t *testing.T) {
	e := NewNoiseEstimator(16000)
	db := e.EstimateDb(nil)
	if db != -96.0 {
		t.Errorf("empty input should return -96.0, got %f", db)
	}
}

func TestNoiseEstimatorEmptyAfterInit(t *testing.T) {
	e := NewNoiseEstimator(16000)
	e.EstimateDb(generateInt16Samples(160, 1000))
	db := e.EstimateDb(nil)
	if db != e.smoothedDb {
		t.Errorf("empty after init should return smoothed value")
	}
}

func TestNoiseEstimatorSmoothing(t *testing.T) {
	e := NewNoiseEstimator(16000)
	e.SetSmoothing(0.1)

	first := e.EstimateDb(generateInt16Samples(160, 30000))
	second := e.EstimateDb(generateInt16Samples(160, 0))

	if math.Abs(first-second) > 30 {
		t.Errorf("heavy smoothing should keep values close, got %f -> %f", first, second)
	}
}

func TestNoiseEstimatorNoSmoothing(t *testing.T) {
	e := NewNoiseEstimator(16000)
	e.SetSmoothing(1.0)

	first := e.EstimateDb(generateInt16Samples(160, 30000))
	second := e.EstimateDb(generateInt16Samples(160, 0))

	if math.Abs(first-second) < 30 {
		t.Errorf("no smoothing should allow fast changes, got %f -> %f", first, second)
	}
}

func TestNoiseEstimatorReset(t *testing.T) {
	e := NewNoiseEstimator(16000)
	e.EstimateDb(generateInt16Samples(160, 30000))
	e.Reset()

	if e.initialized {
		t.Error("reset should clear initialized flag")
	}
	if e.CurrentDb() != -96.0 {
		t.Errorf("reset should restore default db")
	}
}

func TestNoiseLevelString(t *testing.T) {
	if NoiseQuiet.String() != "quiet" {
		t.Errorf("expected quiet, got %s", NoiseQuiet.String())
	}
	if NoiseNormal.String() != "normal" {
		t.Errorf("expected normal, got %s", NoiseNormal.String())
	}
	if NoiseLoud.String() != "loud" {
		t.Errorf("expected loud, got %s", NoiseLoud.String())
	}
	if NoiseUnknown.String() != "unknown" {
		t.Errorf("expected unknown, got %s", NoiseUnknown.String())
	}
}

func TestNoiseEstimatorClassify(t *testing.T) {
	e1 := NewNoiseEstimator(16000)
	e1.SetThresholds(-45, -25)

	quiet := e1.Classify(generateInt16Samples(160, 0))
	if quiet != NoiseQuiet {
		t.Errorf("zero samples should be quiet, got %s", quiet)
	}

	e2 := NewNoiseEstimator(16000)
	e2.SetThresholds(-45, -25)

	loud := e2.Classify(generateInt16Samples(160, 32767))
	if loud != NoiseLoud {
		t.Errorf("max samples should be loud, got %s", loud)
	}
}

func TestNoiseEstimatorSetSmoothingClamp(t *testing.T) {
	e := NewNoiseEstimator(16000)
	e.SetSmoothing(-0.5)
	if e.smoothAlpha != 0 {
		t.Errorf("negative alpha should be clamped to 0")
	}
	e.SetSmoothing(2.0)
	if e.smoothAlpha != 1 {
		t.Errorf("alpha > 1 should be clamped to 1")
	}
}

func TestNoiseEstimatorCustomThresholds(t *testing.T) {
	e := NewNoiseEstimator(16000)
	e.SetThresholds(-60, -10)
	if e.quietDbCeil != -60.0 {
		t.Errorf("quiet ceiling not set")
	}
	if e.loudDbFloor != -10.0 {
		t.Errorf("loud floor not set")
	}
}

func TestNoiseEstimatorCurrentLevelAfterEstimate(t *testing.T) {
	e := NewNoiseEstimator(16000)
	e.EstimateDb(generateInt16Samples(160, 32767))
	level := e.CurrentLevel()
	if level != NoiseLoud {
		t.Errorf("current level should reflect last estimate, got %s", level)
	}
}
