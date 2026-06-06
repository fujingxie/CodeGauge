package stream

import "sync"

const (
	EventTypeQuotaUpdate   = "quota_update"
	EventTypeSessionUpdate = "session_update"
	EventTypeAlert         = "alert"

	AlertSeverityWarning  = "warning"
	AlertSeverityCritical = "critical"
)

type Message struct {
	EventType string `json:"event_type"`
	Data      any    `json:"data"`
}

type Hub struct {
	mu          sync.Mutex
	subscribers map[*Subscription]struct{}
}

type Subscription struct {
	hub      *Hub
	messages chan Message
}

func NewHub() *Hub {
	return &Hub{
		subscribers: map[*Subscription]struct{}{},
	}
}

func (h *Hub) Subscribe() *Subscription {
	subscription := &Subscription{
		hub:      h,
		messages: make(chan Message, 32),
	}

	h.mu.Lock()
	h.subscribers[subscription] = struct{}{}
	h.mu.Unlock()

	return subscription
}

func (h *Hub) Publish(message Message) {
	h.mu.Lock()
	defer h.mu.Unlock()

	for subscription := range h.subscribers {
		select {
		case subscription.messages <- message:
		default:
		}
	}
}

func (s *Subscription) Messages() <-chan Message {
	return s.messages
}

func (s *Subscription) Close() {
	s.hub.mu.Lock()
	delete(s.hub.subscribers, s)
	s.hub.mu.Unlock()
}
