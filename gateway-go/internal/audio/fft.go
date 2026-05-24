package audio

import "math"

func FFT(realIn []float64) (realOut, imagOut []float64) {
	n := len(realIn)
	if n&(n-1) != 0 {
		nextPow2 := 1
		for nextPow2 < n {
			nextPow2 <<= 1
		}
		paddedReal := make([]float64, nextPow2)
		copy(paddedReal, realIn)
		paddedImag := make([]float64, nextPow2)
		return fftCore(paddedReal, paddedImag)
	}
	imag := make([]float64, n)
	return fftCore(realIn, imag)
}

func fftCore(real, imag []float64) ([]float64, []float64) {
	n := len(real)
	if n <= 1 {
		return real, imag
	}

	rev := make([]int, n)
	for i := range rev {
		rev[i] = rev[i>>1]>>1 | ((i & 1) << (bitLen(n) - 1))
	}

	for i := range real {
		if i < rev[i] {
			real[i], real[rev[i]] = real[rev[i]], real[i]
		}
	}

	for length := 2; length <= n; length <<= 1 {
		half := length >> 1
		angle := -2.0 * math.Pi / float64(length)
		wReal := math.Cos(angle)
		wImag := math.Sin(angle)

		for i := 0; i < n; i += length {
			curReal := 1.0
			curImag := 0.0

			for j := 0; j < half; j++ {
				uReal := real[i+j]
				uImag := imag[i+j]

				vReal := real[i+j+half]
				vImag := imag[i+j+half]

				tReal := curReal*vReal - curImag*vImag
				tImag := curReal*vImag + curImag*vReal

				real[i+j] = uReal + tReal
				imag[i+j] = uImag + tImag
				real[i+j+half] = uReal - tReal
				imag[i+j+half] = uImag - tImag

				nextReal := curReal*wReal - curImag*wImag
				nextImag := curReal*wImag + curImag*wReal
				curReal = nextReal
				curImag = nextImag
			}
		}
	}

	return real, imag
}

func IFFT(real, imag []float64) []float64 {
	n := len(real)

	for i := range imag {
		imag[i] = -imag[i]
	}

	outReal, outImag := fftCore(real, imag)

	for i := range outReal {
		outReal[i] /= float64(n)
		outImag[i] /= -float64(n)
	}

	return outReal
}

func bitLen(n int) int {
	count := 0
	for n > 1 {
		n >>= 1
		count++
	}
	return count
}

func MagnitudeSpectrum(real, imag []float64) []float64 {
	mag := make([]float64, len(real))
	for i := range mag {
		mag[i] = math.Sqrt(real[i]*real[i] + imag[i]*imag[i])
	}
	return mag
}
