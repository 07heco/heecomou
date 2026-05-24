package main

import (
	"log"
	"net/http"
	"os"

	"github.com/07heco/heecomou/gateway-go/internal/asr"
	"github.com/07heco/heecomou/gateway-go/internal/websocket"
)

func handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Write([]byte(`{"status":"UP","service":"gateway-go"}`))
}

func main() {
	port := os.Getenv("GATEWAY_PORT")
	if port == "" {
		port = "8080"
	}

	outputRoot := os.Getenv("AUDIO_OUTPUT_DIR")
	if outputRoot == "" {
		outputRoot = "./audio_sessions"
	}

	asrServers := splitEnv("ASR_SERVERS", "http://localhost:8082")

	forwarder := asr.NewForwarder(asrServers)

	audioHandler := websocket.NewAudioHandler(outputRoot, forwarder)

	http.Handle("/ws/audio", audioHandler)
	http.HandleFunc("/health", handleHealth)

	log.Printf("Gateway starting on :%s (audio output: %s, asr servers: %v)",
		port, outputRoot, asrServers)
	if err := http.ListenAndServe(":"+port, nil); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}

func splitEnv(key, defaultVal string) []string {
	val := os.Getenv(key)
	if val == "" {
		return []string{defaultVal}
	}
	parts := []string{}
	for _, p := range splitComma(val) {
		if p != "" {
			parts = append(parts, p)
		}
	}
	if len(parts) == 0 {
		return []string{defaultVal}
	}
	return parts
}

func splitComma(s string) []string {
	var result []string
	current := ""
	for _, ch := range s {
		if ch == ',' {
			result = append(result, current)
			current = ""
		} else {
			current += string(ch)
		}
	}
	if current != "" {
		result = append(result, current)
	}
	return result
}
