package audio

import (
	"math"
)

type NoiseLevel int

const (
	NoiseQuiet   NoiseLevel = 0
	NoiseNormal  NoiseLevel = 1
	NoiseLoud    NoiseLevel = 2
	NoiseUnknown NoiseLevel = -1
)

func (n NoiseLevel) String() string {
	switch n {
	case NoiseQuiet:
		return "quiet"
	case NoiseNormal:
		return "normal"
	case NoiseLoud:
		return "loud"
	default:
		return "unknown"
	}
}

type NoiseEstimator struct {
	sampleRate    int
	quietDbCeil   float64
	loudDbFloor   float64
	smoothAlpha   float64
	smoothedDb    float64
	initialized   bool
}

func NewNoiseEstimator(sampleRate int) *NoiseEstimator {
	return &NoiseEstimator{
		sampleRate:  sampleRate,
		quietDbCeil: -45.0,
		loudDbFloor: -25.0,
		smoothAlpha: 0.3,
		smoothedDb:  -96.0,
	}
}

func (e *NoiseEstimator) SetThresholds(quietCeil, loudFloor float64) {
	e.quietDbCeil = quietCeil
	e.loudDbFloor = loudFloor
}

func (e *NoiseEstimator) SetSmoothing(alpha float64) {
	if alpha < 0 {
		alpha = 0
	}
	if alpha > 1 {
		alpha = 1
	}
	e.smoothAlpha = alpha
}

func (e *NoiseEstimator) EstimateDb(pcmSamples []int16) float64 {
	if len(pcmSamples) == 0 {
		if e.initialized {
			return e.smoothedDb
		}
		return -96.0
	}

	var sumSquares float64
	for _, s := range pcmSamples {
		v := float64(s)
		sumSquares += v * v
	}

	rms := math.Sqrt(sumSquares / float64(len(pcmSamples)))

	const refPcm = 32768.0
	if rms < 1.0 {
		rms = 1.0
	}
	db := 20.0 * math.Log10(rms/refPcm)

	if !e.initialized {
		e.smoothedDb = db
		e.initialized = true
	} else {
		e.smoothedDb = e.smoothAlpha*db + (1-e.smoothAlpha)*e.smoothedDb
	}

	return e.smoothedDb
}

func (e *NoiseEstimator) Classify(pcmSamples []int16) NoiseLevel {
	db := e.EstimateDb(pcmSamples)
	return e.classifyDb(db)
}

func (e *NoiseEstimator) classifyDb(db float64) NoiseLevel {
	if db <= e.quietDbCeil {
		return NoiseQuiet
	}
	if db >= e.loudDbFloor {
		return NoiseLoud
	}
	return NoiseNormal
}

func (e *NoiseEstimator) Reset() {
	e.smoothedDb = -96.0
	e.initialized = false
}

func (e *NoiseEstimator) CurrentDb() float64 {
	return e.smoothedDb
}

func (e *NoiseEstimator) CurrentLevel() NoiseLevel {
	return e.classifyDb(e.smoothedDb)
}
