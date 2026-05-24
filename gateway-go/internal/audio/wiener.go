package audio

import (
	"math"
)

type WienerFilter struct {
	fftSize       int
	hopSize       int
	noiseFloor    float64
	smoothAlpha   float64
	noiseSpectrum []float64
	noiseFrames   int
	noiseReady    bool
	overlap       []float64
}

func NewWienerFilter(fftSize int) *WienerFilter {
	return &WienerFilter{
		fftSize:       fftSize,
		hopSize:       fftSize / 2,
		noiseFloor:    0.01,
		smoothAlpha:   0.1,
		noiseSpectrum: make([]float64, fftSize/2+1),
	}
}

func (w *WienerFilter) SetSmoothing(alpha float64) {
	if alpha < 0 {
		alpha = 0
	}
	if alpha > 1 {
		alpha = 1
	}
	w.smoothAlpha = alpha
}

func (w *WienerFilter) EstimateNoise(samples []int16) {
	frames := w.frameSamples(samples)
	for _, frame := range frames {
		real, imag := FFT(frame)
		mag := MagnitudeSpectrum(real, imag)[:w.fftSize/2+1]

		if !w.noiseReady {
			for i := range w.noiseSpectrum {
				w.noiseSpectrum[i] += mag[i]
			}
			w.noiseFrames++
		}
	}
}

func (w *WienerFilter) FinalizeNoiseEstimate() {
	if w.noiseFrames > 0 {
		for i := range w.noiseSpectrum {
			w.noiseSpectrum[i] /= float64(w.noiseFrames)
			if w.noiseSpectrum[i] < w.noiseFloor {
				w.noiseSpectrum[i] = w.noiseFloor
			}
		}
		w.noiseReady = true
	}
}

func (w *WienerFilter) NoiseReady() bool {
	return w.noiseReady
}

func (w *WienerFilter) Process(samples []int16) []int16 {
	frames := w.frameSamples(samples)
	if len(frames) == 0 {
		return samples
	}

	var output []float64

	for _, frame := range frames {
		real, imag := FFT(frame)
		mag := MagnitudeSpectrum(real, imag)

		if w.noiseReady {
			for i := 1; i < len(mag) && i < len(w.noiseSpectrum); i++ {
				noisePow := w.noiseSpectrum[i] * w.noiseSpectrum[i]
				signalPow := mag[i]*mag[i] - noisePow
				if signalPow < 0 {
					signalPow = 0
				}
				gain := signalPow / (signalPow + noisePow)
				if gain < 0.1 {
					gain = 0.1
				}

				real[i] *= gain
				imag[i] *= gain
			}
		}

		result := IFFT(real, imag)

		for i := 0; i < len(result); i++ {
			idx := i
			if idx < len(output) {
				output[idx] += result[i]
			} else {
				output = append(output, result[i])
			}
		}
	}

	final := make([]int16, len(output))
	for i := range final {
		v := output[i]
		if v > 32767 {
			v = 32767
		} else if v < -32768 {
			v = -32768
		}
		final[i] = int16(v)
	}
	return final
}

func (w *WienerFilter) ProcessStream(samples []int16) []int16 {
	frames := w.frameSamplesWithOverlap(samples)
	if len(frames) == 0 {
		return samples
	}

	var output []float64

	for _, frame := range frames {
		floatFrame := make([]float64, len(frame))
		for i, s := range frame {
			floatFrame[i] = float64(s)
		}

		real, imag := FFT(floatFrame)
		mag := MagnitudeSpectrum(real, imag)

		if w.noiseReady {
			limit := len(mag)
			if limit > len(w.noiseSpectrum) {
				limit = len(w.noiseSpectrum)
			}
			for i := 1; i < limit; i++ {
				noisePow := w.noiseSpectrum[i] * w.noiseSpectrum[i]
				signalPow := mag[i]*mag[i] - noisePow
				if signalPow < 0 {
					signalPow = 0
				}
				gain := signalPow / (signalPow + noisePow)
				if gain < 0.1 {
					gain = 0.1
				}
				real[i] *= gain
				imag[i] *= gain
			}
		}

		result := IFFT(real, imag)
		output = append(output, result[:w.hopSize]...)
	}

	final := make([]int16, len(output))
	for i := range final {
		v := math.Round(output[i])
		if v > 32767 {
			v = 32767
		} else if v < -32768 {
			v = -32768
		}
		final[i] = int16(v)
	}
	return final
}

func (w *WienerFilter) frameSamples(samples []int16) [][]float64 {
	var frames [][]float64
	for i := 0; i < len(samples); i += w.hopSize {
		end := i + w.fftSize
		if end > len(samples) {
			break
		}
		frame := make([]float64, end-i)
		for j := 0; j < len(frame); j++ {
			frame[j] = float64(samples[i+j])
		}
		frames = append(frames, frame)
	}
	return frames
}

func (w *WienerFilter) frameSamplesWithOverlap(samples []int16) [][]float64 {
	var frames [][]float64
	step := w.hopSize
	for i := 0; i < len(samples); i += step {
		end := i + w.fftSize
		if end > len(samples) {
			padded := make([]float64, w.fftSize)
			remaining := len(samples) - i
			for j := 0; j < remaining; j++ {
				padded[j] = float64(samples[i+j])
			}
			frames = append(frames, padded)
			break
		}
		frame := make([]float64, w.fftSize)
		for j := 0; j < w.fftSize; j++ {
			frame[j] = float64(samples[i+j])
		}
		frames = append(frames, frame)
	}
	return frames
}

func (w *WienerFilter) Reset() {
	for i := range w.noiseSpectrum {
		w.noiseSpectrum[i] = 0
	}
	w.noiseFrames = 0
	w.noiseReady = false
}
