package router

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestRuleEngine_BatteryLow(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 1.0,
		BatteryLevel:   10,
		NoiseLevelDb:   30,
	})
	if decision.Engine != EngineLocal {
		t.Errorf("expected local for low battery, got %s: %s", decision.Engine, decision.Reason)
	}
	if decision.Level != 1 {
		t.Errorf("expected level 1, got %d", decision.Level)
	}
}

func TestRuleEngine_Offline(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "none",
		SignalStrength: 0,
		BatteryLevel:   80,
		NoiseLevelDb:   30,
	})
	if decision.Engine != EngineLocal {
		t.Errorf("expected local for offline, got %s", decision.Engine)
	}
}

func TestRuleEngine_Disconnected(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "disconnected",
		SignalStrength: 0,
		BatteryLevel:   80,
		NoiseLevelDb:   30,
	})
	if decision.Engine != EngineLocal {
		t.Errorf("expected local for disconnected, got %s", decision.Engine)
	}
}

func TestRuleEngine_WeakCellular(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "cellular",
		SignalStrength: 0.3,
		BatteryLevel:   80,
		NoiseLevelDb:   30,
	})
	if decision.Engine != EngineLocal {
		t.Errorf("expected local for weak cellular, got %s", decision.Engine)
	}
	if decision.Level != 2 {
		t.Errorf("expected level 2, got %d", decision.Level)
	}
}

func TestRuleEngine_WeakWiFi(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 0.2,
		BatteryLevel:   80,
		NoiseLevelDb:   30,
	})
	if decision.Engine != EngineLocal {
		t.Errorf("expected local for weak WiFi, got %s", decision.Engine)
	}
}

func TestRuleEngine_SensitiveContext(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:       "wifi",
		SignalStrength:    0.8,
		BatteryLevel:      80,
		NoiseLevelDb:      30,
		IsSensitiveContext: true,
	})
	if decision.Engine != EngineLocal {
		t.Errorf("expected local for sensitive context, got %s", decision.Engine)
	}
	if decision.Level != 3 {
		t.Errorf("expected level 3, got %d", decision.Level)
	}
}

func TestRuleEngine_GoodNetworkQuiet(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 0.8,
		BatteryLevel:   80,
		NoiseLevelDb:   30,
	})
	if decision.Engine != EngineCloud {
		t.Errorf("expected cloud for good WiFi + quiet, got %s: %s", decision.Engine, decision.Reason)
	}
	if decision.Level != 4 {
		t.Errorf("expected level 4, got %d", decision.Level)
	}
}

func TestRuleEngine_GoodEthernet(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "ethernet",
		SignalStrength: 0,
		BatteryLevel:   80,
		NoiseLevelDb:   30,
	})
	if decision.Engine != EngineCloud {
		t.Errorf("expected cloud for ethernet + quiet, got %s", decision.Engine)
	}
}

func TestRuleEngine_NoisyEnvironment(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 0.9,
		BatteryLevel:   80,
		NoiseLevelDb:   70,
	})
	if decision.Engine != EngineCloud {
		t.Errorf("noisy but good network should still default cloud, got %s", decision.Engine)
	}
}

func TestRuleEngine_DefaultCloud(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "cellular",
		SignalStrength: 0.6,
		BatteryLevel:   50,
		NoiseLevelDb:   60,
	})
	if decision.Engine != EngineCloud {
		t.Errorf("expected cloud for default case, got %s", decision.Engine)
	}
	if decision.Level != 5 {
		t.Errorf("expected level 5, got %d", decision.Level)
	}
}

func TestRuleEngine_BatteryExactly15(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 1.0,
		BatteryLevel:   15,
		NoiseLevelDb:   30,
	})
	if decision.Engine != EngineLocal {
		t.Errorf("expected local for battery=15%%, got %s", decision.Engine)
	}
}

func TestRuleEngine_Battery16NotLow(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 1.0,
		BatteryLevel:   16,
		NoiseLevelDb:   30,
	})
	if decision.Engine == EngineLocal {
		t.Errorf("expected NOT local for battery=16%%, got %s", decision.Engine)
	}
}

func TestRuleEngine_CellularGoodSignal(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "cellular",
		SignalStrength: 0.6,
		BatteryLevel:   80,
		NoiseLevelDb:   30,
	})
	if decision.Engine != EngineCloud {
		t.Errorf("expected cloud for good cellular signal, got %s", decision.Engine)
	}
}

func TestShouldUseLocal(t *testing.T) {
	engine := NewRuleEngine()
	if !engine.ShouldUseLocal(RouteRequest{
		NetworkType: "none",
		BatteryLevel: 80,
	}) {
		t.Error("ShouldUseLocal should return true for offline")
	}
	if engine.ShouldUseLocal(RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 0.9,
		BatteryLevel:   80,
		NoiseLevelDb:   30,
	}) {
		t.Error("ShouldUseLocal should return false for good conditions")
	}
}

func TestPriority_OverrideLowBatteryOverGoodNetwork(t *testing.T) {
	engine := NewRuleEngine()
	decision := engine.Route(RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 0.9,
		BatteryLevel:   5,
		NoiseLevelDb:   30,
	})
	if decision.Engine != EngineLocal {
		t.Errorf("low battery should override good network, got %s", decision.Engine)
	}
	if decision.Level != 1 {
		t.Errorf("low battery should be priority 1, got %d", decision.Level)
	}
}

func TestHandler_PostRoute(t *testing.T) {
	engine := NewRuleEngine()
	handler := NewRouteHandler(engine)

	req := RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 0.9,
		BatteryLevel:   80,
		NoiseLevelDb:   30,
	}
	body, _ := json.Marshal(req)

	w := httptest.NewRecorder()
	r := httptest.NewRequest("POST", "/api/v1/route", bytes.NewReader(body))
	r.Header.Set("Content-Type", "application/json")

	handler.ServeHTTP(w, r)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d: %s", w.Code, w.Body.String())
	}

	var decision RouteDecision
	if err := json.NewDecoder(w.Body).Decode(&decision); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}
	if decision.Engine != EngineCloud {
		t.Errorf("expected cloud, got %s", decision.Engine)
	}
}

func TestHandler_GetMethodNotAllowed(t *testing.T) {
	engine := NewRuleEngine()
	handler := NewRouteHandler(engine)

	w := httptest.NewRecorder()
	r := httptest.NewRequest("GET", "/api/v1/route", nil)

	handler.ServeHTTP(w, r)

	if w.Code != http.StatusMethodNotAllowed {
		t.Errorf("expected 405, got %d", w.Code)
	}
}

func TestHandler_InvalidJSON(t *testing.T) {
	engine := NewRuleEngine()
	handler := NewRouteHandler(engine)

	w := httptest.NewRecorder()
	r := httptest.NewRequest("POST", "/api/v1/route", bytes.NewReader([]byte("not json")))

	handler.ServeHTTP(w, r)

	if w.Code != http.StatusBadRequest {
		t.Errorf("expected 400, got %d", w.Code)
	}
}

func TestHandler_MissingFields(t *testing.T) {
	engine := NewRuleEngine()
	handler := NewRouteHandler(engine)

	req := RouteRequest{}
	body, _ := json.Marshal(req)

	w := httptest.NewRecorder()
	r := httptest.NewRequest("POST", "/api/v1/route", bytes.NewReader(body))
	r.Header.Set("Content-Type", "application/json")

	handler.ServeHTTP(w, r)

	if w.Code != http.StatusBadRequest {
		t.Errorf("expected 400 for missing network_type, got %d", w.Code)
	}
}

func TestHandler_InvalidSignalStrength(t *testing.T) {
	engine := NewRuleEngine()
	handler := NewRouteHandler(engine)

	req := RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 1.5,
		BatteryLevel:   80,
	}
	body, _ := json.Marshal(req)

	w := httptest.NewRecorder()
	r := httptest.NewRequest("POST", "/api/v1/route", bytes.NewReader(body))
	r.Header.Set("Content-Type", "application/json")

	handler.ServeHTTP(w, r)

	if w.Code != http.StatusBadRequest {
		t.Errorf("expected 400 for invalid signal_strength, got %d", w.Code)
	}
}

func TestHandler_InvalidBatteryLevel(t *testing.T) {
	engine := NewRuleEngine()
	handler := NewRouteHandler(engine)

	req := RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 0.5,
		BatteryLevel:   120,
	}
	body, _ := json.Marshal(req)

	w := httptest.NewRecorder()
	r := httptest.NewRequest("POST", "/api/v1/route", bytes.NewReader(body))
	r.Header.Set("Content-Type", "application/json")

	handler.ServeHTTP(w, r)

	if w.Code != http.StatusBadRequest {
		t.Errorf("expected 400 for invalid battery_level, got %d", w.Code)
	}
}

func TestHandler_NonJSONContentType(t *testing.T) {
	engine := NewRuleEngine()
	handler := NewRouteHandler(engine)

	w := httptest.NewRecorder()
	r := httptest.NewRequest("POST", "/api/v1/route", bytes.NewReader([]byte(`{}`)))
	r.Header.Set("Content-Type", "text/plain")

	handler.ServeHTTP(w, r)

	if w.Code != http.StatusUnsupportedMediaType {
		t.Errorf("expected 415, got %d", w.Code)
	}
}

func TestHandler_EmptyContentType(t *testing.T) {
	engine := NewRuleEngine()
	handler := NewRouteHandler(engine)

	req := RouteRequest{
		NetworkType:    "wifi",
		SignalStrength: 0.5,
		BatteryLevel:   80,
		NoiseLevelDb:   30,
	}
	body, _ := json.Marshal(req)

	w := httptest.NewRecorder()
	r := httptest.NewRequest("POST", "/api/v1/route", bytes.NewReader(body))

	handler.ServeHTTP(w, r)

	if w.Code != http.StatusOK {
		t.Errorf("empty content type should be accepted, got %d: %s", w.Code, w.Body.String())
	}
}

func TestHandler_CaseInsensitivePriorityChain(t *testing.T) {
	engine := NewRuleEngine()
	handler := NewRouteHandler(engine)

	req := RouteRequest{
		NetworkType:    "cellular",
		SignalStrength: 0.7,
		BatteryLevel:   8,
		NoiseLevelDb:   30,
	}
	body, _ := json.Marshal(req)

	w := httptest.NewRecorder()
	r := httptest.NewRequest("POST", "/api/v1/route", bytes.NewReader(body))
	r.Header.Set("Content-Type", "application/json")

	handler.ServeHTTP(w, r)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d", w.Code)
	}

	var decision RouteDecision
	json.NewDecoder(w.Body).Decode(&decision)
	if decision.Engine != EngineLocal {
		t.Errorf("battery 8%% should override everything, got %s", decision.Engine)
	}
	if decision.Level != 1 {
		t.Errorf("expected level 1, got %d", decision.Level)
	}
}
