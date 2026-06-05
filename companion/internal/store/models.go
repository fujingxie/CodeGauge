package store

import "time"

const (
	ProviderClaude = "claude"
	ProviderCodex  = "codex"

	WindowTypeFiveHours = "5h"
	WindowTypeWeekly    = "weekly"

	SourceCCUsage  = "ccusage"
	SourceCLI      = "cli"
	SourceEndpoint = "endpoint"

	SessionStateRunning = "running"
	SessionStateWaiting = "waiting"
	SessionStateDone    = "done"
	SessionStateError   = "error"
	SessionStateUnknown = "unknown"

	EventSessionStart   = "session_start"
	EventSessionDone    = "session_done"
	EventSessionWaiting = "session_waiting"
	EventLimitWarn      = "limit_warn"
	EventLimitCritical  = "limit_critical"
	EventQuotaReset     = "quota_reset"
	EventError          = "error"
)

type Provider struct {
	ID        string
	Name      string
	PlanTier  string
	Available bool
}

type QuotaWindow struct {
	ID          int64
	ProviderID  string
	WindowType  string
	PercentLeft *int
	Used        *int64
	Limit       *int64
	ResetsAt    *time.Time
	Source      string
	UpdatedAt   time.Time
}

type CodingSession struct {
	ID             string
	ProviderID     string
	ProjectPath    string
	State          string
	StartedAt      time.Time
	LastActivityAt time.Time
	LastEventType  string
}

type Event struct {
	ID         int64
	ProviderID *string
	Type       string
	Payload    string
	CreatedAt  time.Time
}

type DevicePairing struct {
	DeviceID   string
	Name       string
	Token      string
	PairedAt   time.Time
	LastSeenAt time.Time
}

type Setting struct {
	Key   string
	Value string
}
