package websocket

import (
	"bufio"
	"crypto/rand"
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/07heco/heecomou/gateway-go/internal/asr"
	"github.com/07heco/heecomou/gateway-go/internal/audio"
)

const (
	wsGUID            = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
	opText            = 1
	opBinary          = 2
	opClose           = 8
	opPing            = 9
	opPong            = 10
	maxFrameSize      = 65536
)

type Conn struct {
	conn   net.Conn
	reader *bufio.Reader
}

func (c *Conn) WriteJSON(v interface{}) error {
	data, err := json.Marshal(v)
	if err != nil {
		return err
	}
	return c.writeFrame(opText, data)
}

func (c *Conn) ReadMessage() (int, []byte, error) {
	return c.readFrame()
}

func (c *Conn) Close() error {
	closeFrame := make([]byte, 6)
	closeFrame[0] = 0x88
	closeFrame[1] = 0x02
	binary.BigEndian.PutUint16(closeFrame[2:4], 1000)
	c.conn.Write(closeFrame)
	return c.conn.Close()
}

func (c *Conn) RemoteAddr() net.Addr {
	return c.conn.RemoteAddr()
}

func (c *Conn) SetReadDeadline(t time.Time) error {
	return c.conn.SetReadDeadline(t)
}

func (c *Conn) writeFrame(opcode int, payload []byte) error {
	frame := make([]byte, 2)
	frame[0] = 0x80 | byte(opcode)

	length := len(payload)
	if length < 126 {
		frame[1] = byte(length)
		frame = append(frame, payload...)
	} else if length < 65536 {
		frame[1] = 126
		ext := make([]byte, 2)
		binary.BigEndian.PutUint16(ext, uint16(length))
		frame = append(frame, ext...)
		frame = append(frame, payload...)
	} else {
		frame[1] = 127
		ext := make([]byte, 8)
		binary.BigEndian.PutUint64(ext, uint64(length))
		frame = append(frame, ext...)
		frame = append(frame, payload...)
	}

	_, err := c.conn.Write(frame)
	return err
}

func (c *Conn) readFrame() (int, []byte, error) {
	header := make([]byte, 2)
	if _, err := io.ReadFull(c.reader, header); err != nil {
		return 0, nil, err
	}

	opcode := int(header[0] & 0x0F)
	masked := (header[1] & 0x80) != 0
	length := uint64(header[1] & 0x7F)

	switch {
	case length == 126:
		ext := make([]byte, 2)
		if _, err := io.ReadFull(c.reader, ext); err != nil {
			return 0, nil, err
		}
		length = uint64(binary.BigEndian.Uint16(ext))
	case length == 127:
		ext := make([]byte, 8)
		if _, err := io.ReadFull(c.reader, ext); err != nil {
			return 0, nil, err
		}
		length = binary.BigEndian.Uint64(ext)
	}

	if length > maxFrameSize {
		return 0, nil, fmt.Errorf("frame too large: %d", length)
	}

	var maskKey [4]byte
	if masked {
		if _, err := io.ReadFull(c.reader, maskKey[:]); err != nil {
			return 0, nil, err
		}
	}

	payload := make([]byte, length)
	if length > 0 {
		if _, err := io.ReadFull(c.reader, payload); err != nil {
			return 0, nil, err
		}
	}

	if masked {
		for i := range payload {
			payload[i] ^= maskKey[i%4]
		}
	}

	switch opcode {
	case opClose:
		return opcode, nil, io.EOF
	case opPing:
		c.writeFrame(opPong, payload)
		return opcode, nil, nil
	case opPong:
		return opcode, nil, nil
	}

	return opcode, payload, nil
}

type AudioHandler struct {
	outputRoot string
	forwarder  *asr.Forwarder
}

func NewAudioHandler(outputRoot string, forwarder *asr.Forwarder) *AudioHandler {
	return &AudioHandler{outputRoot: outputRoot, forwarder: forwarder}
}

func (h *AudioHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if !strings.EqualFold(r.Header.Get("Upgrade"), "websocket") {
		http.Error(w, "Use WebSocket", http.StatusBadRequest)
		return
	}

	hj, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "Hijack not supported", http.StatusInternalServerError)
		return
	}

	netConn, bufrw, err := hj.Hijack()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	key := r.Header.Get("Sec-WebSocket-Key")
	hash := sha1.Sum([]byte(key + wsGUID))
	acceptKey := base64.StdEncoding.EncodeToString(hash[:])

	resp := "HTTP/1.1 101 Switching Protocols\r\n" +
		"Upgrade: websocket\r\n" +
		"Connection: Upgrade\r\n" +
		"Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n"

	if _, err := bufrw.WriteString(resp); err != nil {
		netConn.Close()
		return
	}
	if err := bufrw.Flush(); err != nil {
		netConn.Close()
		return
	}

	conn := &Conn{
		conn:   netConn,
		reader: bufrw.Reader,
	}

	sessionID := genUUID()
	sessionDir := filepath.Join(h.outputRoot, sessionID)
	if err := os.MkdirAll(sessionDir, 0755); err != nil {
		log.Printf("Failed to create session dir: %v", err)
		conn.Close()
		return
	}

	writer := audio.NewSessionWriter(audio.AudioFormat{
		SampleRate:  16000,
		NumChannels: 1,
		BitDepth:    16,
	}, sessionDir)

	log.Printf("Audio session %s started", sessionID)

	defer func() {
		if err := writer.Finalize(); err != nil {
			log.Printf("Session %s finalize error: %v", sessionID, err)
		}

		sampleCount := writer.SampleCount()
		duration := writer.Duration()
		log.Printf("Audio session %s ended (samples=%d, duration=%.2fs)",
			sessionID, sampleCount, duration)

		if h.forwarder != nil && sampleCount > 0 {
			wavPath := filepath.Join(sessionDir, "audio.wav")
			log.Printf("Session %s: forwarding to ASR...", sessionID)
			result, err := h.forwarder.Forward(wavPath, "zh")
			if err != nil {
				log.Printf("Session %s ASR forward failed: %v", sessionID, err)
				conn.WriteJSON(map[string]interface{}{
					"type":    "error",
					"message": fmt.Sprintf("ASR failed: %v", err),
				})
			} else {
				log.Printf("Session %s ASR result: %s", sessionID, result.Text)
				conn.WriteJSON(map[string]interface{}{
					"type":       "final_result",
					"text":       result.Text,
					"is_final":   true,
					"confidence": 1.0,
				})
				asrResultPath := filepath.Join(sessionDir, "asr_result.json")
				data, _ := json.Marshal(result)
				os.WriteFile(asrResultPath, data, 0644)
			}
		}

		conn.Close()
	}()

	conn.WriteJSON(map[string]interface{}{
		"type":       "session_started",
		"session_id": sessionID,
		"format": map[string]int{
			"sample_rate":  16000,
			"num_channels": 1,
			"bit_depth":    16,
		},
	})

	readTimeout := 3 * time.Second
	for {
		conn.SetReadDeadline(time.Now().Add(readTimeout))
		messageType, message, err := conn.ReadMessage()
		if err != nil {
			if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
				log.Printf("Session %s read timeout (end of speech)", sessionID)
			} else if err != io.EOF {
				log.Printf("Session %s read error: %v", sessionID, err)
			}
			break
		}

		if messageType != opBinary {
			continue
		}

		n, err := writer.AppendSamples(message)
		if err != nil {
			log.Printf("Session %s append error: %v", sessionID, err)
			break
		}

		conn.WriteJSON(map[string]interface{}{
			"type":          "samples_received",
			"sample_count":  n,
			"total_samples": writer.SampleCount(),
		})
	}
}

func genUUID() string {
	b := make([]byte, 16)
	rand.Read(b)
	return fmt.Sprintf("%x-%x-%x-%x-%x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
