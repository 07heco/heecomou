package asr

import (
	"encoding/base64"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/07heco/heecomou/gateway-go/internal/asr/grpc"
	"github.com/07heco/heecomou/gateway-go/internal/asr/pb"
)

type GRPCForwarder struct {
	servers []string
	timeout time.Duration
}

func NewGRPCForwarder(servers []string) *GRPCForwarder {
	if len(servers) == 0 {
		servers = []string{"localhost:50051"}
	}
	return &GRPCForwarder{
		servers: servers,
		timeout: 30 * time.Second,
	}
}

func (f *GRPCForwarder) SetTimeout(d time.Duration) {
	f.timeout = d
}

func (f *GRPCForwarder) HealthCheck(server string) (*pb.HealthResponse, error) {
	client, err := grpc.Dial(server)
	if err != nil {
		return nil, fmt.Errorf("dial %s: %w", server, err)
	}
	defer client.Close()

	req := &pb.HealthRequest{}
	respData, err := client.CallUnary(
		"/asr.ASRService/GetHealth",
		req.Marshal(),
		f.timeout,
	)
	if err != nil {
		return nil, fmt.Errorf("health check: %w", err)
	}

	var resp pb.HealthResponse
	if err := resp.Unmarshal(respData); err != nil {
		return nil, fmt.Errorf("unmarshal health response: %w", err)
	}
	return &resp, nil
}

func (f *GRPCForwarder) ForwardStreaming(wavPath string, language string) (*RecognizeResponse, error) {
	wavData, err := os.ReadFile(wavPath)
	if err != nil {
		return nil, fmt.Errorf("read wav: %w", err)
	}

	b64 := base64.StdEncoding.EncodeToString(wavData)

	var lastErr error
	for _, server := range f.servers {
		result, err := f.forwardOne(server, b64, language)
		if err != nil {
			lastErr = err
			log.Printf("GRPC streaming to %s failed: %v", server, err)
			continue
		}
		return result, nil
	}

	if lastErr != nil {
		return nil, fmt.Errorf("all %d gRPC servers failed: %w", len(f.servers), lastErr)
	}
	return nil, fmt.Errorf("all %d gRPC servers failed", len(f.servers))
}

func (f *GRPCForwarder) forwardOne(server, audioB64, language string) (*RecognizeResponse, error) {
	client, err := grpc.Dial(server)
	if err != nil {
		return nil, err
	}
	defer client.Close()

	chunk := &pb.AudioChunk{
		AudioData: []byte(audioB64),
		Language:  language,
	}

	start := time.Now()
	respData, err := client.CallUnary(
		"/asr.ASRService/StreamingRecognize",
		chunk.Marshal(),
		f.timeout,
	)
	elapsed := time.Since(start)

	if err != nil {
		return nil, fmt.Errorf("streaming recognize (%.0fms): %w", elapsed.Seconds()*1000, err)
	}

	var pbResult pb.RecognitionResult
	if err := pbResult.Unmarshal(respData); err != nil {
		return nil, fmt.Errorf("unmarshal result: %w", err)
	}

	log.Printf("GRPC ASR forward to %s OK (%.0fms): text=%s",
		server, elapsed.Seconds()*1000, truncate(pbResult.Text, 50))

	return &RecognizeResponse{
		Text:       pbResult.Text,
		DurationMs: pbResult.DurationMs,
		ModelName:  "qwen3-asr-grpc",
	}, nil
}
