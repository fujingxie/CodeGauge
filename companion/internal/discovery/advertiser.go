package discovery

import (
	"context"
	"fmt"
	"net"

	"github.com/grandcat/zeroconf"
)

const (
	ServiceType   = "_codegauge._tcp"
	ServiceDomain = "local."
)

type ServiceConfig struct {
	Instance   string
	ServerName string
	Host       string
	Port       int
	Version    string
}

type Service struct {
	Instance string
	Type     string
	Domain   string
	Port     int
	TXT      []string
}

type Registration interface {
	Shutdown()
}

type Registrar interface {
	Register(instance string, service string, domain string, port int, text []string, ifaces []net.Interface) (Registration, error)
}

type Advertiser struct {
	registrar Registrar
}

func NewAdvertiser(registrar Registrar) *Advertiser {
	if registrar == nil {
		registrar = ZeroconfRegistrar{}
	}
	return &Advertiser{registrar: registrar}
}

func (cfg ServiceConfig) Service() Service {
	instance := cfg.Instance
	if instance == "" {
		instance = cfg.ServerName
	}
	if instance == "" {
		instance = "CodeGauge Companion"
	}

	return Service{
		Instance: instance,
		Type:     ServiceType,
		Domain:   ServiceDomain,
		Port:     cfg.Port,
		TXT: []string{
			fmt.Sprintf("version=%s", cfg.Version),
			fmt.Sprintf("port=%d", cfg.Port),
			fmt.Sprintf("host=%s", cfg.Host),
			fmt.Sprintf("server_name=%s", cfg.ServerName),
		},
	}
}

func (a *Advertiser) Start(ctx context.Context, service Service) error {
	registration, err := a.registrar.Register(
		service.Instance,
		service.Type,
		service.Domain,
		service.Port,
		service.TXT,
		nil,
	)
	if err != nil {
		return fmt.Errorf("register mdns service: %w", err)
	}

	go func() {
		<-ctx.Done()
		registration.Shutdown()
	}()
	return nil
}

type ZeroconfRegistrar struct{}

func (ZeroconfRegistrar) Register(instance string, service string, domain string, port int, text []string, ifaces []net.Interface) (Registration, error) {
	return zeroconf.Register(instance, service, domain, port, text, ifaces)
}
