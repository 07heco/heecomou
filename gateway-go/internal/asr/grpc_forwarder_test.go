package asr

import (
	"testing"
	"time"
)

func TestNewGRPCForwarderDefaults(t *testing.T) {
	f := NewGRPCForwarder(nil)
	if len(f.servers) != 1 {
		t.Errorf("expected 1 default server, got %d", len(f.servers))
	}
	if f.servers[0] != "localhost:50051" {
		t.Errorf("expected localhost:50051, got %s", f.servers[0])
	}
	if f.timeout != 30*time.Second {
		t.Errorf("expected 30s timeout, got %v", f.timeout)
	}
}

func TestNewGRPCForwarderCustomServers(t *testing.T) {
	servers := []string{"10.0.0.1:50051", "10.0.0.2:50051"}
	f := NewGRPCForwarder(servers)
	if len(f.servers) != 2 {
		t.Errorf("expected 2 servers, got %d", len(f.servers))
	}
	if f.servers[0] != servers[0] || f.servers[1] != servers[1] {
		t.Errorf("server order mismatch")
	}
}

func TestGRPCForwarderSetTimeout(t *testing.T) {
	f := NewGRPCForwarder(nil)
	f.SetTimeout(5 * time.Second)
	if f.timeout != 5*time.Second {
		t.Errorf("expected 5s timeout, got %v", f.timeout)
	}
}
