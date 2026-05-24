package audio

import (
	"math"
	"testing"
)

func TestFFTPowerOfTwo(t *testing.T) {
	input := make([]float64, 8)
	for i := range input {
		input[i] = float64(i)
	}
	real, imag := FFT(input)
	if len(real) != 8 {
		t.Errorf("expected 8 real components, got %d", len(real))
	}
	if len(imag) != 8 {
		t.Errorf("expected 8 imag components, got %d", len(imag))
	}
}

func TestFFTNonPowerOfTwo(t *testing.T) {
	input := make([]float64, 10)
	for i := range input {
		input[i] = float64(i + 1)
	}
	real, imag := FFT(input)
	if len(real) != 16 {
		t.Errorf("expected 16 real components (next pow2), got %d", len(real))
	}
	if len(imag) != 16 {
		t.Errorf("expected 16 imag components (next pow2), got %d", len(imag))
	}
}

func TestFFTSingleElement(t *testing.T) {
	input := []float64{42.0}
	real, _ := FFT(input)
	if len(real) != 1 {
		t.Errorf("expected 1 element, got %d", len(real))
	}
	if real[0] != 42.0 {
		t.Errorf("expected 42.0, got %f", real[0])
	}
}

func TestIFFTRecoversOriginal(t *testing.T) {
	n := 8
	input := make([]float64, n)
	for i := range input {
		input[i] = float64(i)
	}
	real, imag := FFT(input)
	recovered := IFFT(real, imag)

	for i := range recovered {
		if math.Abs(recovered[i]-input[i]) > 1e-6 {
			t.Errorf("IFFT(FFT) mismatch at %d: %f != %f", i, recovered[i], input[i])
		}
	}
}

func TestMagnitudeSpectrum(t *testing.T) {
	real := []float64{3.0, 0.0}
	imag := []float64{4.0, 0.0}
	mag := MagnitudeSpectrum(real, imag)
	if math.Abs(mag[0]-5.0) > 1e-6 {
		t.Errorf("expected 5.0, got %f", mag[0])
	}
	if math.Abs(mag[1]-0.0) > 1e-6 {
		t.Errorf("expected 0.0, got %f", mag[1])
	}
}

func TestFFTDCComponent(t *testing.T) {
	input := make([]float64, 4)
	for i := range input {
		input[i] = 1.0
	}
	real, imag := FFT(input)
	if math.Abs(real[0]-4.0) > 1e-6 {
		t.Errorf("DC component should be 4.0, got %f", real[0])
	}
	for i := 1; i < len(real); i++ {
		if math.Abs(real[i]) > 1e-6 || math.Abs(imag[i]) > 1e-6 {
			t.Errorf("frequency bin %d should be zero", i)
		}
	}
}

func TestFFTSineWave(t *testing.T) {
	n := 32
	freq := 4
	input := make([]float64, n)
	for i := range input {
		input[i] = math.Sin(2 * math.Pi * float64(freq) * float64(i) / float64(n))
	}
	real, imag := FFT(input)
	mag := MagnitudeSpectrum(real, imag)

	maxBin := 0
	maxVal := 0.0
	for i := range mag {
		if mag[i] > maxVal {
			maxVal = mag[i]
			maxBin = i
		}
	}
	if maxBin != freq {
		t.Errorf("expected peak at bin %d, got %d (mag=%f)", freq, maxBin, maxVal)
	}
}

func TestFFTSymmetry(t *testing.T) {
	n := 8
	input := make([]float64, n)
	for i := range input {
		input[i] = float64(i)
	}
	real, imag := FFT(input)

	for i := 1; i < n/2; i++ {
		if math.Abs(real[i]-real[n-i]) > 1e-6 {
			t.Errorf("symmetry broken for real[%d] vs real[%d]", i, n-i)
		}
		if math.Abs(imag[i]+imag[n-i]) > 1e-6 {
			t.Errorf("antisymmetry broken for imag[%d] vs imag[%d]", i, n-i)
		}
	}
}

func TestBitLen(t *testing.T) {
	tests := []struct {
		n    int
		want int
	}{
		{1, 0},
		{2, 1},
		{4, 2},
		{8, 3},
		{16, 4},
		{32, 5},
	}
	for _, tt := range tests {
		if got := bitLen(tt.n); got != tt.want {
			t.Errorf("bitLen(%d) = %d, want %d", tt.n, got, tt.want)
		}
	}
}
