package discovery

import (
	"context"
	"net"
	"testing"
	"time"
)

func TestServiceConfigBuildsCodeGaugeRecord(t *testing.T) {
	service := ServiceConfig{
		Instance:   "Dev Mac",
		ServerName: "CodeGauge Companion",
		Host:       "0.0.0.0",
		Port:       8765,
		Version:    "dev",
	}.Service()

	if service.Instance != "Dev Mac" {
		t.Fatalf("Instance = %q, want Dev Mac", service.Instance)
	}
	if service.Type != "_codegauge._tcp" {
		t.Fatalf("Type = %q, want _codegauge._tcp", service.Type)
	}
	if service.Domain != "local." {
		t.Fatalf("Domain = %q, want local.", service.Domain)
	}
	if service.Port != 8765 {
		t.Fatalf("Port = %d, want 8765", service.Port)
	}

	assertTXTContains(t, service.TXT, "version=dev")
	assertTXTContains(t, service.TXT, "port=8765")
	assertTXTContains(t, service.TXT, "host=0.0.0.0")
	assertTXTContains(t, service.TXT, "server_name=CodeGauge Companion")
}

func TestAdvertiserRegistersAndShutsDownWithContext(t *testing.T) {
	registrar := &fakeRegistrar{}
	advertiser := NewAdvertiser(registrar)
	ctx, cancel := context.WithCancel(context.Background())

	err := advertiser.Start(ctx, ServiceConfig{
		Instance:   "Dev Mac",
		ServerName: "CodeGauge Companion",
		Host:       "0.0.0.0",
		Port:       8765,
		Version:    "dev",
	}.Service())
	if err != nil {
		t.Fatalf("Start: %v", err)
	}
	if len(registrar.calls) != 1 {
		t.Fatalf("calls length = %d, want 1", len(registrar.calls))
	}
	call := registrar.calls[0]
	if call.instance != "Dev Mac" || call.service != "_codegauge._tcp" || call.domain != "local." || call.port != 8765 {
		t.Fatalf("register call = %+v, want CodeGauge mdns registration", call)
	}

	cancel()
	select {
	case <-registrar.registration.shutdown:
	case <-time.After(time.Second):
		t.Fatal("timed out waiting for mdns shutdown")
	}
}

type fakeRegistrar struct {
	calls        []registerCall
	registration *fakeRegistration
}

type registerCall struct {
	instance string
	service  string
	domain   string
	port     int
	text     []string
	ifaces   []net.Interface
}

func (r *fakeRegistrar) Register(instance string, service string, domain string, port int, text []string, ifaces []net.Interface) (Registration, error) {
	r.calls = append(r.calls, registerCall{
		instance: instance,
		service:  service,
		domain:   domain,
		port:     port,
		text:     text,
		ifaces:   ifaces,
	})
	r.registration = &fakeRegistration{shutdown: make(chan struct{})}
	return r.registration, nil
}

type fakeRegistration struct {
	shutdown chan struct{}
}

func (r *fakeRegistration) Shutdown() {
	close(r.shutdown)
}

func assertTXTContains(t *testing.T, values []string, want string) {
	t.Helper()
	for _, value := range values {
		if value == want {
			return
		}
	}
	t.Fatalf("TXT = %+v, want %q", values, want)
}
