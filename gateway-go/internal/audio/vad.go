package audio

import (
	"math"
)

type VADDecision int

const (
	VADSilence VADDecision = 0
	VADSpeech  VADDecision = 1
)

type VADResult struct {
	Decision VADDecision
	EnergyDb float64
	ZCR      float64
}

type SmartVAD struct {
	frameSize      int
	sampleRate     int
	energyCeil     float64
	zcrFloor       float64
	hangoverInit   int
	hangoverMax    int
	hangoverCount  int
	speechFrames   int
	silenceFrames  int
	minSpeechFrame int
	adaptive       bool
	noiseFloor     float64
	smoothAlpha    float64
}

func NewSmartVAD(frameSize, sampleRate int) *SmartVAD {
	return &SmartVAD{
		frameSize:      frameSize,
		sampleRate:     sampleRate,
		energyCeil:     -38.0,
		zcrFloor:       0.15,
		hangoverInit:   8,
		hangoverMax:    15,
		minSpeechFrame: 3,
		adaptive:       true,
		noiseFloor:     -50.0,
		smoothAlpha:    0.2,
	}
}

func (v *SmartVAD) SetEnergyThreshold(db float64) {
	v.energyCeil = db
}

func (v *SmartVAD) SetZCRThreshold(zcr float64) {
	v.zcrFloor = zcr
}

func (v *SmartVAD) SetHangover(init, max int) {
	v.hangoverInit = init
	v.hangoverMax = max
}

func (v *SmartVAD) SetAdaptive(enabled bool) {
	v.adaptive = enabled
}

func (v *SmartVAD) Detect(frame []int16) VADResult {
	result := VADResult{Decision: VADSilence}

	if len(frame) == 0 {
		return result
	}

	energyDb := v.computeEnergyDb(frame)
	zcr := v.computeZCR(frame)

	result.EnergyDb = energyDb
	result.ZCR = zcr

	isSpeech := energyDb > v.energyCeil || zcr > v.zcrFloor

	if v.adaptive && v.speechFrames+v.silenceFrames > 10 {
		if !isSpeech {
			v.noiseFloor = v.smoothAlpha*energyDb + (1-v.smoothAlpha)*v.noiseFloor
			v.energyCeil = v.noiseFloor + 12.0
			if v.energyCeil < -45 {
				v.energyCeil = -45
			}
			if v.energyCeil > -25 {
				v.energyCeil = -25
			}
		}
	}

	if isSpeech {
		v.speechFrames++
		v.silenceFrames = 0
		if v.speechFrames > v.hangoverInit {
			v.hangoverCount = v.hangoverMax
		}
	} else {
		v.speechFrames = 0
		v.silenceFrames++
	}

	if v.speechFrames >= v.minSpeechFrame || v.hangoverCount > 0 {
		result.Decision = VADSpeech
		if !isSpeech {
			v.hangoverCount--
		}
	}

	return result
}

func (v *SmartVAD) DetectBuffer(samples []int16) []VADResult {
	var results []VADResult
	for i := 0; i+v.frameSize <= len(samples); i += v.frameSize {
		frame := samples[i : i+v.frameSize]
		result := v.Detect(frame)
		results = append(results, result)
	}
	return results
}

func (v *SmartVAD) HasSpeech(samples []int16) bool {
	results := v.DetectBuffer(samples)
	for _, r := range results {
		if r.Decision == VADSpeech {
			return true
		}
	}
	return false
}

func (v *SmartVAD) SpeechSegments(samples []int16) [][2]int {
	results := v.DetectBuffer(samples)
	if len(results) == 0 {
		return nil
	}

	var segments [][2]int
	inSpeech := false
	var start int

	for i, r := range results {
		offset := i * v.frameSize
		if r.Decision == VADSpeech && !inSpeech {
			inSpeech = true
			start = offset
		} else if r.Decision == VADSilence && inSpeech {
			inSpeech = false
			end := offset
			if end > len(samples) {
				end = len(samples)
			}
			if end-start >= v.frameSize {
				segments = append(segments, [2]int{start, end})
			}
		}
	}

	if inSpeech {
		end := len(results) * v.frameSize
		if end > len(samples) {
			end = len(samples)
		}
		if end-start >= v.frameSize {
			segments = append(segments, [2]int{start, end})
		}
	}

	return segments
}

func (v *SmartVAD) Reset() {
	v.hangoverCount = 0
	v.speechFrames = 0
	v.silenceFrames = 0
	v.noiseFloor = -50.0
	v.energyCeil = -38.0
}

func (v *SmartVAD) computeEnergyDb(samples []int16) float64 {
	var sumSquares float64
	for _, s := range samples {
		v := float64(s)
		sumSquares += v * v
	}
	rms := math.Sqrt(sumSquares / float64(len(samples)))
	if rms < 1.0 {
		rms = 1.0
	}
	return 20.0 * math.Log10(rms/32768.0)
}

func (v *SmartVAD) computeZCR(samples []int16) float64 {
	if len(samples) < 2 {
		return 0
	}
	crossings := 0
	for i := 1; i < len(samples); i++ {
		if (samples[i] >= 0 && samples[i-1] < 0) ||
			(samples[i] < 0 && samples[i-1] >= 0) {
			crossings++
		}
	}
	return float64(crossings) / float64(len(samples)-1)
}
