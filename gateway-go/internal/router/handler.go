package router

import (
	"encoding/json"
	"log"
	"net/http"
)

type RouteHandler struct {
	engine *RuleEngine
}

func NewRouteHandler(engine *RuleEngine) *RouteHandler {
	return &RouteHandler{engine: engine}
}

func (h *RouteHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "仅支持 POST 方法")
		return
	}

	contentType := r.Header.Get("Content-Type")
	if contentType != "" && contentType != "application/json" {
		writeError(w, http.StatusUnsupportedMediaType, "Content-Type 必须是 application/json")
		return
	}

	var req RouteRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "请求体解析失败: "+err.Error())
		return
	}

	if err := validateRequest(&req); err != "" {
		writeError(w, http.StatusBadRequest, err)
		return
	}

	decision := h.engine.Route(req)
	log.Printf("[Router] network=%s signal=%.2f battery=%.0f%% noise=%.1fdB sensitive=%v -> engine=%s reason=%s level=%d",
		req.NetworkType, req.SignalStrength, req.BatteryLevel, req.NoiseLevelDb,
		req.IsSensitiveContext, decision.Engine, decision.Reason, decision.Level)

	writeJSON(w, http.StatusOK, decision)
}

func validateRequest(req *RouteRequest) string {
	if req.NetworkType == "" {
		return "network_type 不能为空"
	}
	if req.SignalStrength < 0 || req.SignalStrength > 1 {
		return "signal_strength 必须在 [0, 1] 范围内"
	}
	if req.BatteryLevel < 0 || req.BatteryLevel > 100 {
		return "battery_level 必须在 [0, 100] 范围内"
	}
	return ""
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
