package router

type Engine string

const (
	EngineCloud Engine = "cloud"
	EngineLocal Engine = "local"
)

type RouteRequest struct {
	NetworkType       string  `json:"network_type"`
	SignalStrength    float64 `json:"signal_strength"`
	BatteryLevel      float64 `json:"battery_level"`
	NoiseLevelDb      float64 `json:"noise_level_db"`
	IsSensitiveContext bool   `json:"is_sensitive_context"`
	Language          string  `json:"language"`
}

type RouteDecision struct {
	Engine Engine `json:"engine"`
	Reason string `json:"reason"`
	Level  int    `json:"level"`
}

type RuleEngine struct{}

func NewRuleEngine() *RuleEngine {
	return &RuleEngine{}
}

func (e *RuleEngine) Route(req RouteRequest) RouteDecision {
	if decision := e.checkOfflinePriority(req); decision != nil {
		return *decision
	}
	if decision := e.checkLowLatency(req); decision != nil {
		return *decision
	}
	if decision := e.checkPrivacy(req); decision != nil {
		return *decision
	}
	if decision := e.checkHighQuality(req); decision != nil {
		return *decision
	}

	return RouteDecision{
		Engine: EngineCloud,
		Reason: "默认云端 (default cloud)",
		Level:  5,
	}
}

func (e *RuleEngine) checkOfflinePriority(req RouteRequest) *RouteDecision {
	if req.BatteryLevel <= 15 {
		return &RouteDecision{
			Engine: EngineLocal,
			Reason: "低电量优先端侧 (battery <= 15%)",
			Level:  1,
		}
	}

	if req.NetworkType == "none" || req.NetworkType == "disconnected" {
		return &RouteDecision{
			Engine: EngineLocal,
			Reason: "离线状态强制端侧 (network unavailable)",
			Level:  1,
		}
	}

	return nil
}

func (e *RuleEngine) checkLowLatency(req RouteRequest) *RouteDecision {
	if req.NetworkType == "cellular" && req.SignalStrength < 0.5 {
		return &RouteDecision{
			Engine: EngineLocal,
			Reason: "弱蜂窝信号优先端侧 (low cellular signal)",
			Level:  2,
		}
	}

	if req.NetworkType == "wifi" && req.SignalStrength < 0.3 {
		return &RouteDecision{
			Engine: EngineLocal,
			Reason: "弱WiFi信号优先端侧 (low WiFi signal)",
			Level:  2,
		}
	}

	return nil
}

func (e *RuleEngine) checkPrivacy(req RouteRequest) *RouteDecision {
	if req.IsSensitiveContext {
		return &RouteDecision{
			Engine: EngineLocal,
			Reason: "敏感上下文优先端侧 (sensitive context)",
			Level:  3,
		}
	}

	return nil
}

func (e *RuleEngine) checkHighQuality(req RouteRequest) *RouteDecision {
	isGoodNetwork := (req.NetworkType == "wifi" && req.SignalStrength > 0.7) ||
		(req.NetworkType == "ethernet")

	if isGoodNetwork && req.NoiseLevelDb < 50 {
		return &RouteDecision{
			Engine: EngineCloud,
			Reason: "优质网络+安静环境走云端 (quiet + good network)",
			Level:  4,
		}
	}

	return nil
}

func (e *RuleEngine) ShouldUseLocal(req RouteRequest) bool {
	decision := e.Route(req)
	return decision.Engine == EngineLocal
}
