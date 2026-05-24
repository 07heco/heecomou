package asr

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"sync/atomic"
	"time"
)

type RecognizeRequest struct {
	Audio    string `json:"audio"`
	Language string `json:"language"`
}

type RecognizeResponse struct {
	Text       string  `json:"text"`
	DurationMs float64 `json:"duration_ms"`
	ModelName  string  `json:"model_name"`
}

type Forwarder struct {
	servers []string
	round   uint32
	client  *http.Client
}

func NewForwarder(servers []string) *Forwarder {
	if len(servers) == 0 {
		servers = []string{"http://localhost:8082"}
	}
	return &Forwarder{
		servers: servers,
		client: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

func (f *Forwarder) nextServer() string {
	n := atomic.AddUint32(&f.round, 1)
	return f.servers[(n-1)%uint32(len(f.servers))]
}

func (f *Forwarder) Forward(wavPath string, language string) (*RecognizeResponse, error) {
	wavData, err := os.ReadFile(wavPath)
	if err != nil {
		return nil, fmt.Errorf("read wav: %w", err)
	}

	b64 := base64.StdEncoding.EncodeToString(wavData)
	req := RecognizeRequest{
		Audio:    b64,
		Language: language,
	}

	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("marshal request: %w", err)
	}

	for attempt := 0; attempt < len(f.servers); attempt++ {
		server := f.nextServer()
		url := server + "/api/v1/asr/recognize"

		start := time.Now()
		resp, err := f.forwardOne(url, body)
		elapsed := time.Since(start)

		if err != nil {
			log.Printf("ASR forward to %s failed (attempt %d/%d, %v): %v",
				server, attempt+1, len(f.servers), elapsed, err)
			continue
		}

		log.Printf("ASR forward to %s OK (%.0fms): text=%s",
			server, elapsed.Seconds()*1000, truncate(resp.Text, 50))
		return resp, nil
	}

	return nil, fmt.Errorf("all %d ASR servers failed", len(f.servers))
}

func (f *Forwarder) forwardOne(url string, reqBody []byte) (*RecognizeResponse, error) {
	httpReq, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(reqBody))
	if err != nil {
		return nil, err
	}
	httpReq.Header.Set("Content-Type", "application/json")

	resp, err := f.client.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("http request: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(respBody))
	}

	var result RecognizeResponse
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, fmt.Errorf("unmarshal response: %w", err)
	}

	return &result, nil
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}
