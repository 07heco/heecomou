package grpc

import (
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"sync"
	"time"
)

const (
	http2Preface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"

	frameData         = 0x0
	frameHeaders      = 0x1
	frameSettings     = 0x4
	frameSettingsAck  = 0x4 | 0x1
	flagEndStream     = 0x1
	flagEndHeaders    = 0x4
	flagAck           = 0x1
)

type Client struct {
	addr      string
	conn      net.Conn
	streamID  uint32
	mu        sync.Mutex
	headerBuf []byte
}

func Dial(addr string) (*Client, error) {
	conn, err := net.DialTimeout("tcp", addr, 10*time.Second)
	if err != nil {
		return nil, fmt.Errorf("grpc dial: %w", err)
	}

	c := &Client{
		addr:     addr,
		conn:     conn,
		streamID: 1,
	}

	if err := c.http2Handshake(); err != nil {
		conn.Close()
		return nil, err
	}

	return c, nil
}

func (c *Client) http2Handshake() error {
	if _, err := c.conn.Write([]byte(http2Preface)); err != nil {
		return fmt.Errorf("write preface: %w", err)
	}

	fr := makeFrame(frameSettings, 0, 0, nil)
	if _, err := c.conn.Write(fr); err != nil {
		return fmt.Errorf("write settings: %w", err)
	}

	buf := make([]byte, 9)
	if _, err := io.ReadFull(c.conn, buf); err != nil {
		return fmt.Errorf("read settings: %w", err)
	}
	length := int(buf[0])<<16 | int(buf[1])<<8 | int(buf[2])
	if buf[3] != frameSettings {
		return fmt.Errorf("expected settings frame, got %d", buf[3])
	}

	if length > 0 {
		payload := make([]byte, length)
		if _, err := io.ReadFull(c.conn, payload); err != nil {
			return fmt.Errorf("read settings payload: %w", err)
		}
	}

	ack := makeFrame(frameSettingsAck, 0, 0, nil)
	if _, err := c.conn.Write(ack); err != nil {
		return fmt.Errorf("write settings ack: %w", err)
	}

	return nil
}

func (c *Client) newStreamID() uint32 {
	c.mu.Lock()
	defer c.mu.Unlock()
	id := c.streamID
	c.streamID += 2
	return id
}

func (c *Client) CallUnary(path string, reqPayload []byte, timeout time.Duration) ([]byte, error) {
	streamID := c.newStreamID()

	hdrs := encodeHeaders(streamID, path, "application/grpc+proto", timeout)
	if _, err := c.conn.Write(hdrs); err != nil {
		return nil, fmt.Errorf("write headers: %w", err)
	}

	if reqPayload != nil && len(reqPayload) > 0 {
		grpcFrame := makeGRPCFrame(reqPayload)
		dataFrames := makeDataFrame(streamID, grpcFrame, false)
		if _, err := c.conn.Write(dataFrames); err != nil {
			return nil, fmt.Errorf("write request data: %w", err)
		}
	}

	endData := makeDataFrame(streamID, nil, true)
	if _, err := c.conn.Write(endData); err != nil {
		return nil, fmt.Errorf("write end data: %w", err)
	}

	if err := c.conn.SetReadDeadline(time.Now().Add(timeout)); err != nil {
		return nil, err
	}
	defer c.conn.SetReadDeadline(time.Time{})

	var responseData []byte
	buf := make([]byte, 9)

	for {
		if _, err := io.ReadFull(c.conn, buf); err != nil {
			return nil, fmt.Errorf("read frame header: %w", err)
		}
		length := int(buf[0])<<16 | int(buf[1])<<8 | int(buf[2])
		frameType := buf[3]
		flags := buf[4]
		frameStreamID := binary.BigEndian.Uint32(buf[5:9]) & 0x7FFFFFFF

		var payload []byte
		if length > 0 {
			payload = make([]byte, length)
			if _, err := io.ReadFull(c.conn, payload); err != nil {
				return nil, fmt.Errorf("read frame payload: %w", err)
			}
		}

		if frameStreamID != streamID && frameType != frameSettings {
			continue
		}

		switch frameType {
		case frameHeaders:
			if flags&flagEndStream != 0 {
				return nil, fmt.Errorf("grpc error: stream ended without data")
			}
		case frameData:
			responseData = append(responseData, payload...)
			if flags&flagEndStream != 0 {
				return parseGRPCResponse(responseData)
			}
		case frameSettings:
			if (flags & flagAck) != 0 {
				continue
			}
			ack := makeFrame(frameSettingsAck, 0, 0, nil)
			c.conn.Write(ack)
		}
	}
}

func (c *Client) Close() error {
	goAway := makeFrame(0x7, 0, 0, []byte{0, 0, 0, 0, 0, 0, 0, 0})
	c.conn.Write(goAway)
	return c.conn.Close()
}

func makeFrame(frameType uint8, flags uint8, streamID uint32, payload []byte) []byte {
	length := len(payload)
	frame := make([]byte, 9+length)
	frame[0] = byte(length >> 16)
	frame[1] = byte(length >> 8)
	frame[2] = byte(length)
	frame[3] = frameType
	frame[4] = flags
	binary.BigEndian.PutUint32(frame[5:9], streamID)
	copy(frame[9:], payload)
	return frame
}

func makeDataFrame(streamID uint32, payload []byte, endStream bool) []byte {
	flags := uint8(0)
	if endStream {
		flags |= flagEndStream
	}
	return makeFrame(frameData, flags, streamID, payload)
}

func makeGRPCFrame(payload []byte) []byte {
	frame := make([]byte, 5+len(payload))
	frame[0] = 0
	binary.BigEndian.PutUint32(frame[1:5], uint32(len(payload)))
	copy(frame[5:], payload)
	return frame
}

func parseGRPCResponse(data []byte) ([]byte, error) {
	if len(data) < 5 {
		return nil, fmt.Errorf("response too short: %d bytes", len(data))
	}
	length := binary.BigEndian.Uint32(data[1:5])
	if uint32(len(data)) < 5+length {
		return nil, fmt.Errorf("truncated response: expected %d, got %d", 5+length, len(data))
	}
	return data[5 : 5+length], nil
}

func encodeHeaders(streamID uint32, path, contentType string, timeout time.Duration) []byte {
	payload := encodeHPACKHeader(":method", "POST")
	payload = append(payload, encodeHPACKHeader(":scheme", "http")...)
	payload = append(payload, encodeHPACKHeader(":path", path)...)
	payload = append(payload, encodeHPACKHeader(":authority", "localhost")...)
	payload = append(payload, encodeHPACKHeader("content-type", contentType)...)
	payload = append(payload, encodeHPACKHeader("te", "trailers")...)
	payload = append(payload, encodeHPACKHeader("grpc-timeout", formatGRPCTimeout(timeout))...)

	return makeFrame(frameHeaders, flagEndHeaders, streamID, payload)
}

func formatGRPCTimeout(d time.Duration) string {
	if d <= 0 {
		return "1S"
	}
	ms := d.Milliseconds()
	if ms < 1000 {
		return fmt.Sprintf("%dm", ms)
	}
	return fmt.Sprintf("%dS", ms/1000)
}

func encodeHPACKHeader(name, value string) []byte {
	var buf []byte
	buf = append(buf, 0x40)
	buf = append(buf, byte(len(name)))
	buf = append(buf, name...)
	buf = append(buf, byte(len(value)))
	buf = append(buf, value...)
	return buf
}
