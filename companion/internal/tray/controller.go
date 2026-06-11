package tray

import (
	"context"
	"fmt"
	"sync"
)

type MenuItem interface {
	Clicked() <-chan struct{}
	Disable()
}

type UI interface {
	SetTitle(title string)
	SetTooltip(tooltip string)
	AddMenuItem(title string, tooltip string) MenuItem
	Quit()
}

type Options struct {
	ServerName string
	Address    string
	PairCode   string
	Version    string
	OnQuit     func()
}

type Controller struct {
	ui      UI
	options Options
}

func NewController(ui UI, options Options) *Controller {
	return &Controller{ui: ui, options: options}
}

func (c *Controller) Ready(ctx context.Context) {
	serverName := withDefault(c.options.ServerName, "CodeGauge Companion")
	c.ui.SetTitle("CodeGauge")
	c.ui.SetTooltip(fmt.Sprintf("%s listening on %s", serverName, c.options.Address))

	c.addDisabledItem("Status: Running", "Companion is running")
	c.addDisabledItem("Listening: "+c.options.Address, "LAN API address")
	c.addDisabledItem("Pairing code: "+c.options.PairCode, "Use this code to pair Android devices")
	c.addDisabledItem("Version: "+withDefault(c.options.Version, "dev"), "Companion version")
	quit := c.ui.AddMenuItem("Quit", "Quit CodeGauge Companion")

	var quitOnce sync.Once
	quitTray := func() {
		quitOnce.Do(func() {
			c.ui.Quit()
		})
	}

	go func() {
		<-ctx.Done()
		quitTray()
	}()
	go func() {
		<-quit.Clicked()
		if c.options.OnQuit != nil {
			c.options.OnQuit()
		}
		quitTray()
	}()
}

func (c *Controller) addDisabledItem(title string, tooltip string) {
	item := c.ui.AddMenuItem(title, tooltip)
	item.Disable()
}

func withDefault(value string, fallback string) string {
	if value == "" {
		return fallback
	}
	return value
}
