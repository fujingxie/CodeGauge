package collector

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"os/exec"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
)

type Store interface {
	UpsertProvider(provider store.Provider) error
	UpsertQuotaWindow(window store.QuotaWindow) error
	ListQuotaWindows(providerID string) ([]store.QuotaWindow, error)
}

type Runner interface {
	Run(ctx context.Context, name string, args ...string) ([]byte, error)
}

type PreciseSource interface {
	Collect(ctx context.Context, now time.Time) ([]store.QuotaWindow, error)
}

type Options struct {
	CCUsagePath    string
	Runner         Runner
	Now            func() time.Time
	PreciseSources []PreciseSource
}

type Collector struct {
	store          Store
	ccusagePath    string
	runner         Runner
	now            func() time.Time
	preciseSources []PreciseSource
}

func New(store Store, options Options) *Collector {
	ccusagePath := options.CCUsagePath
	if ccusagePath == "" {
		ccusagePath = "ccusage"
	}

	runner := options.Runner
	if runner == nil {
		runner = ExecRunner{}
	}

	now := options.Now
	if now == nil {
		now = time.Now
	}

	return &Collector{
		store:          store,
		ccusagePath:    ccusagePath,
		runner:         runner,
		now:            now,
		preciseSources: options.PreciseSources,
	}
}

func (c *Collector) CollectOnce(ctx context.Context) error {
	now := c.now().UTC()
	var errs []error

	claudeAvailable, err := c.collectClaude(ctx, now)
	if err != nil {
		errs = append(errs, err)
	}
	if err := c.store.UpsertProvider(store.Provider{
		ID:        store.ProviderClaude,
		Name:      "Claude",
		Available: claudeAvailable,
	}); err != nil {
		errs = append(errs, err)
	}

	codexAvailable, err := c.collectCodex(ctx, now)
	if err != nil {
		errs = append(errs, err)
	}
	if err := c.store.UpsertProvider(store.Provider{
		ID:        store.ProviderCodex,
		Name:      "Codex",
		Available: codexAvailable,
	}); err != nil {
		errs = append(errs, err)
	}

	if err := c.collectPrecise(ctx, now); err != nil {
		errs = append(errs, err)
	}

	return errors.Join(errs...)
}

func (c *Collector) Run(ctx context.Context, interval time.Duration) error {
	if interval <= 0 {
		return fmt.Errorf("collector interval must be positive, got %s", interval)
	}

	return c.RunWithInterval(ctx, func() time.Duration { return interval }, nil)
}

func (c *Collector) RunWithInterval(
	ctx context.Context,
	interval func() time.Duration,
	settingsChanged <-chan struct{},
) error {
	if interval == nil {
		return fmt.Errorf("collector interval provider is nil")
	}

	for {
		if err := c.CollectOnce(ctx); err != nil {
			log.Printf("collect quota snapshot: %v", err)
		}

		currentInterval := interval()
		if currentInterval <= 0 {
			return fmt.Errorf("collector interval must be positive, got %s", currentInterval)
		}

		timer := time.NewTimer(currentInterval)
		select {
		case <-ctx.Done():
			stopTimer(timer)
			return nil
		case <-settingsChanged:
			stopTimer(timer)
		case <-timer.C:
		}
	}
}

func stopTimer(timer *time.Timer) {
	if !timer.Stop() {
		select {
		case <-timer.C:
		default:
		}
	}
}

func (c *Collector) collectClaude(ctx context.Context, now time.Time) (bool, error) {
	available := false
	var errs []error

	blocksOutput, err := c.runner.Run(
		ctx,
		c.ccusagePath,
		"claude",
		"blocks",
		"--json",
		"--recent",
		"--offline",
		"--token-limit",
		"max",
	)
	if err != nil {
		errs = append(errs, fmt.Errorf("run ccusage claude blocks: %w", err))
	} else {
		window, ok, err := parseClaudeBlocks(blocksOutput, now)
		if err != nil {
			errs = append(errs, fmt.Errorf("parse ccusage claude blocks: %w", err))
		} else if ok {
			if err := c.store.UpsertProvider(store.Provider{ID: store.ProviderClaude, Name: "Claude", Available: true}); err != nil {
				errs = append(errs, err)
			} else if err := c.store.UpsertQuotaWindow(window); err != nil {
				errs = append(errs, err)
			} else {
				available = true
			}
		}
	}

	dailyOutput, err := c.runner.Run(ctx, c.ccusagePath, "claude", "daily", "--json", "--offline")
	if err != nil {
		errs = append(errs, fmt.Errorf("run ccusage claude daily: %w", err))
	} else {
		window, ok, err := parseDailyUsage(dailyOutput, store.ProviderClaude, now)
		if err != nil {
			errs = append(errs, fmt.Errorf("parse ccusage claude daily: %w", err))
		} else if ok {
			if err := c.store.UpsertProvider(store.Provider{ID: store.ProviderClaude, Name: "Claude", Available: true}); err != nil {
				errs = append(errs, err)
			} else if err := c.store.UpsertQuotaWindow(window); err != nil {
				errs = append(errs, err)
			} else {
				available = true
			}
		}
	}

	return available, errors.Join(errs...)
}

func (c *Collector) collectCodex(ctx context.Context, now time.Time) (bool, error) {
	output, err := c.runner.Run(ctx, c.ccusagePath, "codex", "daily", "--json", "--offline")
	if err != nil {
		return false, fmt.Errorf("run ccusage codex daily: %w", err)
	}

	window, ok, err := parseDailyUsage(output, store.ProviderCodex, now)
	if err != nil {
		return false, fmt.Errorf("parse ccusage codex daily: %w", err)
	}
	if !ok {
		return false, nil
	}

	if err := c.store.UpsertProvider(store.Provider{ID: store.ProviderCodex, Name: "Codex", Available: true}); err != nil {
		return false, err
	}
	if err := c.store.UpsertQuotaWindow(window); err != nil {
		return false, err
	}

	return true, nil
}

func (c *Collector) collectPrecise(ctx context.Context, now time.Time) error {
	var errs []error
	for _, source := range c.preciseSources {
		windows, err := source.Collect(ctx, now)
		if err != nil {
			log.Printf("precise quota source failed: %v", err)
			continue
		}

		for _, window := range windows {
			if window.ProviderID == "" || window.WindowType == "" {
				continue
			}
			window.Source = store.SourceEndpoint
			if window.UpdatedAt.IsZero() {
				window.UpdatedAt = now.UTC()
			}
			merged, err := c.mergePreciseWindow(window)
			if err != nil {
				errs = append(errs, err)
				continue
			}
			if err := c.store.UpsertProvider(providerForWindow(merged)); err != nil {
				errs = append(errs, err)
				continue
			}
			if err := c.store.UpsertQuotaWindow(merged); err != nil {
				errs = append(errs, err)
			}
		}
	}

	return errors.Join(errs...)
}

func (c *Collector) mergePreciseWindow(window store.QuotaWindow) (store.QuotaWindow, error) {
	existingWindows, err := c.store.ListQuotaWindows(window.ProviderID)
	if err != nil {
		return store.QuotaWindow{}, err
	}

	for _, existing := range existingWindows {
		if existing.WindowType != window.WindowType {
			continue
		}
		if window.PercentLeft == nil {
			window.PercentLeft = existing.PercentLeft
		}
		if window.Used == nil {
			window.Used = existing.Used
		}
		if window.Limit == nil {
			window.Limit = existing.Limit
		}
		if window.ResetsAt == nil {
			window.ResetsAt = existing.ResetsAt
		}
		break
	}

	return window, nil
}

func providerForWindow(window store.QuotaWindow) store.Provider {
	switch window.ProviderID {
	case store.ProviderClaude:
		return store.Provider{ID: store.ProviderClaude, Name: "Claude", Available: true}
	case store.ProviderCodex:
		return store.Provider{ID: store.ProviderCodex, Name: "Codex", Available: true}
	default:
		return store.Provider{ID: window.ProviderID, Name: window.ProviderID, Available: true}
	}
}

type ExecRunner struct{}

func (ExecRunner) Run(ctx context.Context, name string, args ...string) ([]byte, error) {
	output, err := exec.CommandContext(ctx, name, args...).Output()
	if err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			return nil, fmt.Errorf("%w: %s", err, string(exitErr.Stderr))
		}
		return nil, err
	}

	return output, nil
}

type claudeBlocksResponse struct {
	Blocks []claudeBlock `json:"blocks"`
}

type claudeBlock struct {
	EndTime     string `json:"endTime"`
	IsActive    bool   `json:"isActive"`
	IsGap       bool   `json:"isGap"`
	TotalTokens int64  `json:"totalTokens"`
}

func parseClaudeBlocks(output []byte, now time.Time) (store.QuotaWindow, bool, error) {
	var response claudeBlocksResponse
	if err := json.Unmarshal(output, &response); err != nil {
		return store.QuotaWindow{}, false, fmt.Errorf("decode claude blocks JSON: %w", err)
	}

	var selected *claudeBlock
	for i := range response.Blocks {
		block := &response.Blocks[i]
		if block.IsGap {
			continue
		}
		if block.IsActive {
			selected = block
			break
		}
		selected = block
	}
	if selected == nil {
		return store.QuotaWindow{}, false, nil
	}

	resetsAt, err := parseOptionalTime(selected.EndTime)
	if err != nil {
		return store.QuotaWindow{}, false, fmt.Errorf("parse claude block endTime: %w", err)
	}
	used := selected.TotalTokens

	return store.QuotaWindow{
		ProviderID: store.ProviderClaude,
		WindowType: store.WindowTypeFiveHours,
		Used:       &used,
		ResetsAt:   resetsAt,
		Source:     store.SourceCCUsage,
		UpdatedAt:  now.UTC(),
	}, true, nil
}

type dailyUsageResponse struct {
	Daily []dailyUsageItem `json:"daily"`
}

type dailyUsageItem struct {
	Date        string `json:"date"`
	Period      string `json:"period"`
	TotalTokens int64  `json:"totalTokens"`
}

func parseDailyUsage(output []byte, providerID string, now time.Time) (store.QuotaWindow, bool, error) {
	var response dailyUsageResponse
	if err := json.Unmarshal(output, &response); err != nil {
		return store.QuotaWindow{}, false, fmt.Errorf("decode daily usage JSON: %w", err)
	}

	windowStart := now.UTC().AddDate(0, 0, -6)
	var used int64
	for _, item := range response.Daily {
		dayText := item.Date
		if dayText == "" {
			dayText = item.Period
		}

		day, err := time.Parse("2006-01-02", dayText)
		if err != nil {
			return store.QuotaWindow{}, false, fmt.Errorf("parse daily usage date %q: %w", dayText, err)
		}
		if !day.Before(startOfDay(windowStart)) && !day.After(startOfDay(now)) {
			used += item.TotalTokens
		}
	}
	if used == 0 {
		return store.QuotaWindow{}, false, nil
	}

	return store.QuotaWindow{
		ProviderID: providerID,
		WindowType: store.WindowTypeWeekly,
		Used:       &used,
		Source:     store.SourceCCUsage,
		UpdatedAt:  now.UTC(),
	}, true, nil
}

func parseOptionalTime(value string) (*time.Time, error) {
	if value == "" {
		return nil, nil
	}

	parsed, err := time.Parse(time.RFC3339Nano, value)
	if err != nil {
		return nil, err
	}
	parsed = parsed.UTC()
	return &parsed, nil
}

func startOfDay(value time.Time) time.Time {
	utc := value.UTC()
	return time.Date(utc.Year(), utc.Month(), utc.Day(), 0, 0, 0, 0, time.UTC)
}
