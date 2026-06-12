package collector

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os/exec"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
)

type CodexAppServerSource struct {
	CodexPath string
	Timeout   time.Duration
}

func (s CodexAppServerSource) Collect(ctx context.Context, now time.Time) ([]store.QuotaWindow, error) {
	codexPath := s.CodexPath
	if codexPath == "" {
		codexPath = "codex"
	}

	timeout := s.Timeout
	if timeout <= 0 {
		timeout = 10 * time.Second
	}

	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	cmd := exec.CommandContext(ctx, codexPath, "app-server", "--stdio")
	stdin, err := cmd.StdinPipe()
	if err != nil {
		return nil, fmt.Errorf("open codex app-server stdin: %w", err)
	}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, fmt.Errorf("open codex app-server stdout: %w", err)
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return nil, fmt.Errorf("open codex app-server stderr: %w", err)
	}

	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("start codex app-server: %w", err)
	}
	defer func() {
		if cmd.Process != nil {
			_ = cmd.Process.Kill()
		}
		_ = cmd.Wait()
	}()

	stderrBuffer := &limitedBuffer{limit: 4 * 1024}
	go func() {
		_, _ = io.Copy(stderrBuffer, stderr)
	}()

	if err := writeCodexAppServerRequests(stdin); err != nil {
		return nil, err
	}

	result, err := readCodexRateLimitResult(stdout)
	if err != nil {
		if ctx.Err() != nil {
			return nil, fmt.Errorf("codex app-server timed out after %s", timeout)
		}
		if stderrText := stderrBuffer.String(); stderrText != "" {
			return nil, fmt.Errorf("%w: %s", err, stderrText)
		}
		return nil, err
	}

	return parseCodexRateLimits(result, now)
}

func writeCodexAppServerRequests(writer io.Writer) error {
	encoder := json.NewEncoder(writer)
	requests := []map[string]any{
		{
			"id":     1,
			"method": "initialize",
			"params": map[string]any{
				"clientInfo": map[string]string{
					"name":    "codegauge",
					"version": "dev",
				},
			},
		},
		{
			"id":     2,
			"method": "account/rateLimits/read",
			"params": nil,
		},
	}

	for _, request := range requests {
		if err := encoder.Encode(request); err != nil {
			return fmt.Errorf("write codex app-server request: %w", err)
		}
	}

	return nil
}

func readCodexRateLimitResult(reader io.Reader) ([]byte, error) {
	scanner := bufio.NewScanner(reader)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for scanner.Scan() {
		line := scanner.Bytes()
		var response struct {
			ID     int             `json:"id"`
			Result json.RawMessage `json:"result"`
			Error  json.RawMessage `json:"error"`
		}
		if err := json.Unmarshal(line, &response); err != nil {
			continue
		}
		if response.ID != 2 {
			continue
		}
		if len(response.Error) > 0 && string(response.Error) != "null" {
			return nil, fmt.Errorf("codex app-server returned error: %s", response.Error)
		}
		if len(response.Result) == 0 || string(response.Result) == "null" {
			return nil, fmt.Errorf("codex app-server returned empty rate limit result")
		}
		return response.Result, nil
	}
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("read codex app-server output: %w", err)
	}

	return nil, fmt.Errorf("codex app-server exited without rate limit result")
}

type codexRateLimitsResponse struct {
	RateLimits          codexRateLimitSnapshot            `json:"rateLimits"`
	RateLimitsByLimitID map[string]codexRateLimitSnapshot `json:"rateLimitsByLimitId"`
}

type codexRateLimitSnapshot struct {
	LimitID   *string               `json:"limitId"`
	Primary   *codexRateLimitWindow `json:"primary"`
	Secondary *codexRateLimitWindow `json:"secondary"`
}

type codexRateLimitWindow struct {
	UsedPercent        *int   `json:"usedPercent"`
	WindowDurationMins *int   `json:"windowDurationMins"`
	ResetsAt           *int64 `json:"resetsAt"`
}

func parseCodexRateLimits(output []byte, now time.Time) ([]store.QuotaWindow, error) {
	var response codexRateLimitsResponse
	if err := json.Unmarshal(output, &response); err != nil {
		return nil, fmt.Errorf("decode codex rate limits JSON: %w", err)
	}

	snapshot := response.RateLimits
	if byID, ok := response.RateLimitsByLimitID[store.ProviderCodex]; ok {
		snapshot = byID
	}

	var windows []store.QuotaWindow
	if window, ok := codexWindow(snapshot.Primary, store.WindowTypeFiveHours, now); ok {
		windows = append(windows, window)
	}
	if window, ok := codexWindow(snapshot.Secondary, store.WindowTypeWeekly, now); ok {
		windows = append(windows, window)
	}

	if len(windows) == 0 {
		return nil, fmt.Errorf("codex rate limits contained no usable windows")
	}

	return windows, nil
}

func codexWindow(
	window *codexRateLimitWindow,
	fallbackType string,
	now time.Time,
) (store.QuotaWindow, bool) {
	if window == nil || window.UsedPercent == nil {
		return store.QuotaWindow{}, false
	}

	percentLeft := 100 - *window.UsedPercent
	if percentLeft < 0 {
		percentLeft = 0
	}
	if percentLeft > 100 {
		percentLeft = 100
	}

	windowType := fallbackType
	if window.WindowDurationMins != nil {
		switch *window.WindowDurationMins {
		case 300:
			windowType = store.WindowTypeFiveHours
		case 10080:
			windowType = store.WindowTypeWeekly
		}
	}

	var resetsAt *time.Time
	if window.ResetsAt != nil {
		value := time.Unix(*window.ResetsAt, 0).UTC()
		resetsAt = &value
	}

	return store.QuotaWindow{
		ProviderID:  store.ProviderCodex,
		WindowType:  windowType,
		PercentLeft: &percentLeft,
		ResetsAt:    resetsAt,
		Source:      store.SourceEndpoint,
		UpdatedAt:   now.UTC(),
	}, true
}

type limitedBuffer struct {
	buffer bytes.Buffer
	limit  int
}

func (b *limitedBuffer) Write(p []byte) (int, error) {
	if b.limit <= 0 {
		return len(p), nil
	}
	remaining := b.limit - b.buffer.Len()
	if remaining > 0 {
		if len(p) > remaining {
			_, _ = b.buffer.Write(p[:remaining])
		} else {
			_, _ = b.buffer.Write(p)
		}
	}

	return len(p), nil
}

func (b *limitedBuffer) String() string {
	return b.buffer.String()
}
