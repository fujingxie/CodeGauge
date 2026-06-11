package tray

import (
	"context"

	"fyne.io/systray"
)

func Run(ctx context.Context, options Options) {
	ui := SystrayUI{}
	controller := NewController(ui, options)
	systray.Run(
		func() {
			controller.Ready(ctx)
		},
		func() {},
	)
}

type SystrayUI struct{}

func (SystrayUI) SetTitle(title string) {
	systray.SetTitle(title)
}

func (SystrayUI) SetTooltip(tooltip string) {
	systray.SetTooltip(tooltip)
}

func (SystrayUI) AddMenuItem(title string, tooltip string) MenuItem {
	return systrayMenuItem{item: systray.AddMenuItem(title, tooltip)}
}

func (SystrayUI) Quit() {
	systray.Quit()
}

type systrayMenuItem struct {
	item *systray.MenuItem
}

func (item systrayMenuItem) Clicked() <-chan struct{} {
	return item.item.ClickedCh
}

func (item systrayMenuItem) Disable() {
	item.item.Disable()
}
