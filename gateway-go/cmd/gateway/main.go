package main

import (
	"log"
	"net/http"
	"os"

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

	audioHandler := websocket.NewAudioHandler(outputRoot)

	http.Handle("/ws/audio", audioHandler)
	http.HandleFunc("/health", handleHealth)

	log.Printf("Gateway starting on :%s (audio output: %s)", port, outputRoot)
	if err := http.ListenAndServe(":"+port, nil); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
