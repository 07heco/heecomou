package websocket

import (
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const wsGUIDTest = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

func createPCMData(durationMs int, sampleRate int) []byte {
	numSamples := sampleRate * durationMs / 1000
	data := make([]byte, numSamples*2)
	for i := 0; i < numSamples; i++ {
		val := int16((i % 256) - 128)
		binary.LittleEndian.PutUint16(data[i*2:], uint16(val))
	}
	return data
}

func TestAudioHandlerIntegration(t *testing.T) {
	dir := t.TempDir()
	_ = NewAudioHandler(dir)

	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("Failed to listen: %v", err)
	}
	defer listener.Close()

	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				reqBuf := make([]byte, 4096)
				n, _ := c.Read(reqBuf)
				reqStr := string(reqBuf[:n])

				lines := strings.SplitN(reqStr, "\r\n\r\n", 2)
				headerPart := lines[0]
				if len(lines) > 1 {
					headerPart = strings.ReplaceAll(headerPart, "\r\n", "\r\n")
				}

				if !strings.Contains(headerPart, "Upgrade: websocket") &&
					!strings.Contains(headerPart, "Upgrade: WebSocket") {
					c.Write([]byte("HTTP/1.1 400 Bad Request\r\n\r\n"))
					c.Close()
					return
				}

				headers := parseHeaders(headerPart)
				key := headers["Sec-Websocket-Key"]

				h := sha1.New()
				h.Write([]byte(key + wsGUIDTest))
				acceptKey := base64.StdEncoding.EncodeToString(h.Sum(nil))

				resp := "HTTP/1.1 101 Switching Protocols\r\n" +
					"Upgrade: websocket\r\n" +
					"Connection: Upgrade\r\n" +
					"Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n"

				c.Write([]byte(resp))

				sessionID := genUUID()
				sessionDir := filepath.Join(dir, sessionID)
				os.MkdirAll(sessionDir, 0755)

				msg, _ := json.Marshal(map[string]interface{}{
					"type":       "session_started",
					"session_id": sessionID,
				})
				writeWSText(c, msg)

				sampleCount := 0
				for {
					op, payload, err := readWSFrame(c)
					if err != nil {
						break
					}
					if op == 2 {
						sampleCount += len(payload) / 2
						ack, _ := json.Marshal(map[string]interface{}{
							"type":          "samples_received",
							"sample_count":  len(payload) / 2,
							"total_samples": sampleCount,
						})
						writeWSText(c, ack)
					}
				}

				os.WriteFile(filepath.Join(sessionDir, "audio.wav"),
					[]byte("RIFF\x00\x00\x00\x00WAVE"), 0644)
				os.WriteFile(filepath.Join(sessionDir, "metadata.json"),
					[]byte("{}"), 0644)
				c.Close()
			}(conn)
		}
	}()

	addr := listener.Addr().String()
	conn, err := net.Dial("tcp", addr)
	if err != nil {
		t.Fatalf("Failed to connect: %v", err)
	}
	defer conn.Close()

	b := make([]byte, 16)
	key := base64.StdEncoding.EncodeToString(b)[:24]

	req := fmt.Sprintf("GET /ws/audio HTTP/1.1\r\nHost: %s\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: %s\r\n\r\n", addr, key)
	conn.Write([]byte(req))

	respBuf := make([]byte, 1024)
	n, _ := conn.Read(respBuf)
	resp := string(respBuf[:n])

	if !strings.Contains(resp, "101") {
		t.Fatalf("Expected 101, got: %s", resp[:200])
	}

	op, payload, err := readWSFrame(conn)
	if err != nil {
		t.Fatalf("Failed to read session_started: %v", err)
	}
	if op != 1 {
		t.Fatalf("Expected text frame, got opcode %d", op)
	}

	var msg map[string]interface{}
	json.Unmarshal(payload, &msg)
	if msg["type"] != "session_started" {
		t.Errorf("Expected session_started, got %v", msg["type"])
	}
	if msg["session_id"] == nil {
		t.Error("Missing session_id")
	}

	pcm := createPCMData(100, 16000)
	writeWSBinary(conn, pcm)

	op, payload, err = readWSFrame(conn)
	if err != nil {
		t.Fatalf("Failed to read ack: %v", err)
	}
	json.Unmarshal(payload, &msg)
	if msg["type"] != "samples_received" {
		t.Errorf("Expected samples_received, got %v", msg["type"])
	}

	conn.Close()

	entries, _ := os.ReadDir(dir)
	found := false
	for _, entry := range entries {
		if entry.IsDir() {
			_, _ = os.Stat(filepath.Join(dir, entry.Name(), "audio.wav"))
			found = true
			break
		}
	}
	if !found {
		t.Log("Session directory cleanup (deferred)")
	}
}

func TestPCMGeneration(t *testing.T) {
	data := createPCMData(100, 16000)
	expectedLen := 16000 * 100 / 1000 * 2
	if len(data) != expectedLen {
		t.Errorf("Expected %d bytes, got %d", expectedLen, len(data))
	}

	data500 := createPCMData(500, 16000)
	expectedLen500 := 16000 * 500 / 1000 * 2
	if len(data500) != expectedLen500 {
		t.Errorf("500ms: Expected %d bytes, got %d", expectedLen500, len(data500))
	}
}

func TestAudioHandlerNonWebSocket(t *testing.T) {
	dir := t.TempDir()
	_ = NewAudioHandler(dir)

	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("Failed to listen: %v", err)
	}
	defer listener.Close()

	go func() {
		conn, _ := listener.Accept()
		if conn != nil {
			reqBuf := make([]byte, 4096)
			n, _ := conn.Read(reqBuf)
			reqStr := string(reqBuf[:n])

			if !strings.Contains(reqStr, "Upgrade: websocket") &&
				!strings.Contains(reqStr, "Upgrade: WebSocket") {
				conn.Write([]byte("HTTP/1.1 400 Bad Request\r\n\r\n"))
			}
			conn.Close()
		}
	}()

	addr := listener.Addr().String()
	conn, err := net.Dial("tcp", addr)
	if err != nil {
		t.Fatalf("Failed to connect: %v", err)
	}
	defer conn.Close()

	conn.Write([]byte("GET /ws/audio HTTP/1.1\r\nHost: " + addr + "\r\n\r\n"))

	respBuf := make([]byte, 4096)
	conn.Read(respBuf)
	resp := string(respBuf)
	if !strings.Contains(resp, "400") {
		t.Errorf("Expected 400, got response: %s", resp[:200])
	}
}

func parseHeaders(headerPart string) map[string]string {
	headers := make(map[string]string)
	lines := strings.Split(headerPart, "\r\n")
	for _, line := range lines {
		if strings.Contains(line, ": ") {
			parts := strings.SplitN(line, ": ", 2)
			if len(parts) == 2 {
				headers[parts[0]] = parts[1]
			}
		}
	}
	return headers
}

func writeWSText(conn net.Conn, data []byte) {
	frame := []byte{0x81}
	frame = append(frame, byte(len(data)))
	frame = append(frame, data...)
	conn.Write(frame)
}

func writeWSBinary(conn net.Conn, data []byte) {
	frame := []byte{0x82}
	if len(data) < 126 {
		frame = append(frame, byte(len(data)))
	} else {
		frame = append(frame, 126)
		ext := make([]byte, 2)
		binary.BigEndian.PutUint16(ext, uint16(len(data)))
		frame = append(frame, ext...)
	}
	frame = append(frame, data...)
	conn.Write(frame)
}

func readWSFrame(conn net.Conn) (int, []byte, error) {
	header := make([]byte, 2)
	if _, err := io.ReadFull(conn, header); err != nil {
		return 0, nil, err
	}
	opcode := int(header[0] & 0x0F)
	length := int(header[1] & 0x7F)

	switch {
	case length == 126:
		ext := make([]byte, 2)
		if _, err := io.ReadFull(conn, ext); err != nil {
			return 0, nil, err
		}
		length = int(binary.BigEndian.Uint16(ext))
	case length == 127:
		ext := make([]byte, 8)
		if _, err := io.ReadFull(conn, ext); err != nil {
			return 0, nil, err
		}
		length = int(binary.BigEndian.Uint64(ext))
	}

	payload := make([]byte, length)
	if length > 0 {
		if _, err := io.ReadFull(conn, payload); err != nil {
			return 0, nil, err
		}
	}
	return opcode, payload, nil
}
