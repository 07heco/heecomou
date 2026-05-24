package pb

import (
	"encoding/binary"
	"math"
)

type AudioChunk struct {
	AudioData []byte
	Language  string
}

func (m *AudioChunk) Marshal() []byte {
	var buf []byte
	if len(m.AudioData) > 0 {
		buf = appendVarint(buf, 1<<3|2)
		buf = appendVarint(buf, uint64(len(m.AudioData)))
		buf = append(buf, m.AudioData...)
	}
	if m.Language != "" {
		buf = appendVarint(buf, 2<<3|2)
		buf = appendString(buf, m.Language)
	}
	return buf
}

func (m *AudioChunk) Unmarshal(data []byte) error {
	for len(data) > 0 {
		tag, n := readVarint(data)
		if n <= 0 {
			break
		}
		data = data[n:]
		fieldNum := int(tag >> 3)
		wireType := int(tag & 0x7)
		switch fieldNum {
		case 1:
			if wireType == 2 {
				l, nn := readVarint(data)
				if nn <= 0 {
					break
				}
				data = data[nn:]
				m.AudioData = make([]byte, l)
				copy(m.AudioData, data[:l])
				data = data[l:]
			}
		case 2:
			if wireType == 2 {
				l, nn := readVarint(data)
				if nn <= 0 {
					break
				}
				data = data[nn:]
				m.Language = string(data[:l])
				data = data[l:]
			}
		}
	}
	return nil
}

type RecognitionResult struct {
	Text       string
	IsFinal    bool
	DurationMs float64
}

func (m *RecognitionResult) Marshal() []byte {
	var buf []byte
	if m.Text != "" {
		buf = appendVarint(buf, 1<<3|2)
		buf = appendString(buf, m.Text)
	}
	if m.IsFinal {
		buf = appendVarint(buf, 2<<3|0)
		buf = appendVarint(buf, 1)
	}
	if m.DurationMs != 0 {
		buf = appendVarint(buf, 3<<3|1)
		bits := math.Float64bits(m.DurationMs)
		buf = append(buf,
			byte(bits),
			byte(bits>>8),
			byte(bits>>16),
			byte(bits>>24),
			byte(bits>>32),
			byte(bits>>40),
			byte(bits>>48),
			byte(bits>>56),
		)
	}
	return buf
}

func (m *RecognitionResult) Unmarshal(data []byte) error {
	for len(data) > 0 {
		tag, n := readVarint(data)
		if n <= 0 {
			break
		}
		data = data[n:]
		fieldNum := int(tag >> 3)
		wireType := int(tag & 0x7)
		switch fieldNum {
		case 1:
			if wireType == 2 {
				l, nn := readVarint(data)
				if nn <= 0 {
					break
				}
				data = data[nn:]
				m.Text = string(data[:l])
				data = data[l:]
			}
		case 2:
			if wireType == 0 {
				v, _ := readVarint(data)
				m.IsFinal = v != 0
			}
		case 3:
			if wireType == 1 && len(data) >= 8 {
				bits := binary.LittleEndian.Uint64(data[:8])
				m.DurationMs = math.Float64frombits(bits)
				data = data[8:]
			}
		}
	}
	return nil
}

type HealthRequest struct{}

func (m *HealthRequest) Marshal() []byte {
	return nil
}

type HealthResponse struct {
	ModelLoaded bool
	ModelName   string
}

func (m *HealthResponse) Marshal() []byte {
	var buf []byte
	if m.ModelLoaded {
		buf = appendVarint(buf, 1<<3|0)
		buf = appendVarint(buf, 1)
	}
	if m.ModelName != "" {
		buf = appendVarint(buf, 2<<3|2)
		buf = appendString(buf, m.ModelName)
	}
	return buf
}

func (m *HealthResponse) Unmarshal(data []byte) error {
	for len(data) > 0 {
		tag, n := readVarint(data)
		if n <= 0 {
			break
		}
		data = data[n:]
		fieldNum := int(tag >> 3)
		wireType := int(tag & 0x7)
		switch fieldNum {
		case 1:
			if wireType == 0 {
				v, _ := readVarint(data)
				m.ModelLoaded = v != 0
			}
		case 2:
			if wireType == 2 {
				l, nn := readVarint(data)
				if nn <= 0 {
					break
				}
				data = data[nn:]
				m.ModelName = string(data[:l])
				data = data[l:]
			}
		}
	}
	return nil
}

func appendVarint(buf []byte, v uint64) []byte {
	for v >= 0x80 {
		buf = append(buf, byte(v)|0x80)
		v >>= 7
	}
	return append(buf, byte(v))
}

func appendString(buf []byte, s string) []byte {
	buf = appendVarint(buf, uint64(len(s)))
	return append(buf, s...)
}

func readVarint(data []byte) (uint64, int) {
	var v uint64
	for i := 0; i < 10 && i < len(data); i++ {
		b := data[i]
		v |= uint64(b&0x7F) << (i * 7)
		if b < 0x80 {
			return v, i + 1
		}
	}
	return 0, -1
}
