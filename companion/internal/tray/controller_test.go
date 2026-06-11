package tray

import (
	"context"
	"testing"
	"time"
)

func TestControllerConfiguresTrayMenu(t *testing.T) {
	ui := newFakeUI()
	controller := NewController(ui, Options{
		ServerName: "CodeGauge Companion",
		Address:    "0.0.0.0:8765",
		PairCode:   "481920",
		Version:    "dev",
	})
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	controller.Ready(ctx)

	if ui.title != "CodeGauge" {
		t.Fatalf("title = %q, want CodeGauge", ui.title)
	}
	if ui.tooltip != "CodeGauge Companion listening on 0.0.0.0:8765" {
		t.Fatalf("tooltip = %q, want listening tooltip", ui.tooltip)
	}
	assertDisabledMenuItem(t, ui, "Status: Running")
	assertDisabledMenuItem(t, ui, "Listening: 0.0.0.0:8765")
	assertDisabledMenuItem(t, ui, "Pairing code: 481920")
	assertDisabledMenuItem(t, ui, "Version: dev")
	if ui.menuItem("Quit") == nil {
		t.Fatal("Quit menu item not found")
	}
}

func TestControllerInvokesQuitCallback(t *testing.T) {
	ui := newFakeUI()
	quitCalled := make(chan struct{})
	controller := NewController(ui, Options{
		ServerName: "CodeGauge Companion",
		Address:    "0.0.0.0:8765",
		PairCode:   "481920",
		Version:    "dev",
		OnQuit: func() {
			close(quitCalled)
		},
	})
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	controller.Ready(ctx)

	ui.click("Quit")

	select {
	case <-quitCalled:
	case <-time.After(time.Second):
		t.Fatal("timed out waiting for quit callback")
	}
	select {
	case <-ui.quitCalled:
	case <-time.After(time.Second):
		t.Fatal("timed out waiting for tray quit after menu click")
	}
}

func TestControllerQuitsTrayWhenContextEnds(t *testing.T) {
	ui := newFakeUI()
	controller := NewController(ui, Options{
		ServerName: "CodeGauge Companion",
		Address:    "0.0.0.0:8765",
		PairCode:   "481920",
		Version:    "dev",
	})
	ctx, cancel := context.WithCancel(context.Background())
	controller.Ready(ctx)

	cancel()

	select {
	case <-ui.quitCalled:
	case <-time.After(time.Second):
		t.Fatal("timed out waiting for tray quit")
	}
}

func TestControllerQuitsTrayOnceWhenMenuClickCancelsContext(t *testing.T) {
	ui := newFakeUI()
	ctx, cancel := context.WithCancel(context.Background())
	controller := NewController(ui, Options{
		ServerName: "CodeGauge Companion",
		Address:    "0.0.0.0:8765",
		PairCode:   "481920",
		Version:    "dev",
		OnQuit:     cancel,
	})
	controller.Ready(ctx)

	ui.click("Quit")

	select {
	case <-ui.quitCalled:
	case <-time.After(time.Second):
		t.Fatal("timed out waiting for tray quit")
	}
	time.Sleep(50 * time.Millisecond)
	if ui.quitCount != 1 {
		t.Fatalf("quitCount = %d, want 1", ui.quitCount)
	}
}

type fakeUI struct {
	title      string
	tooltip    string
	items      []*fakeMenuItem
	quitCalled chan struct{}
	quitCount  int
}

func newFakeUI() *fakeUI {
	return &fakeUI{quitCalled: make(chan struct{})}
}

func (ui *fakeUI) SetTitle(title string) {
	ui.title = title
}

func (ui *fakeUI) SetTooltip(tooltip string) {
	ui.tooltip = tooltip
}

func (ui *fakeUI) AddMenuItem(title string, tooltip string) MenuItem {
	item := &fakeMenuItem{
		title:    title,
		tooltip:  tooltip,
		clicked:  make(chan struct{}, 1),
		disabled: false,
	}
	ui.items = append(ui.items, item)
	return item
}

func (ui *fakeUI) Quit() {
	ui.quitCount++
	if ui.quitCount == 1 {
		close(ui.quitCalled)
	}
}

func (ui *fakeUI) click(title string) {
	item := ui.menuItem(title)
	if item == nil {
		return
	}
	item.clicked <- struct{}{}
}

func (ui *fakeUI) menuItem(title string) *fakeMenuItem {
	for _, item := range ui.items {
		if item.title == title {
			return item
		}
	}
	return nil
}

type fakeMenuItem struct {
	title    string
	tooltip  string
	clicked  chan struct{}
	disabled bool
}

func (item *fakeMenuItem) Clicked() <-chan struct{} {
	return item.clicked
}

func (item *fakeMenuItem) Disable() {
	item.disabled = true
}

func assertDisabledMenuItem(t *testing.T, ui *fakeUI, title string) {
	t.Helper()
	item := ui.menuItem(title)
	if item == nil {
		t.Fatalf("%q menu item not found", title)
	}
	if !item.disabled {
		t.Fatalf("%q disabled = false, want true", title)
	}
}
