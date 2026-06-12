package com.codegauge.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ActivityJsonParserTest {
    @Test
    fun parsesEventsResponseNewestFirst() {
        val events = ActivityJsonParser.parseEvents(
            """
            {
              "events": [
                {
                  "id": 3,
                  "type": "session_done",
                  "provider_id": "claude",
                  "payload": "{\"session_id\":\"done\"}",
                  "created_at": "2026-06-12T08:30:00Z"
                },
                {
                  "id": 2,
                  "type": "session_waiting",
                  "provider_id": "claude",
                  "payload": "{\"session_id\":\"waiting\"}",
                  "created_at": "2026-06-12T08:29:00Z"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, events.size)
        assertEquals(3L, events[0].id)
        assertEquals("session_done", events[0].type)
        assertEquals("claude", events[0].providerId)
        assertEquals(Instant.parse("2026-06-12T08:30:00Z"), events[0].createdAt)
    }

    @Test
    fun parsesStreamEventUpdate() {
        val message = ActivityJsonParser.parseStreamMessage(
            """
            {
              "event_type": "event_update",
              "data": {
                "id": 4,
                "type": "session_waiting",
                "provider_id": "claude",
                "payload": "{\"hook_event_name\":\"Notification\"}",
                "created_at": "2026-06-12T08:31:00Z"
              }
            }
            """.trimIndent(),
        )

        assertTrue(message is ActivityStreamMessage.Event)
        val event = (message as ActivityStreamMessage.Event).event
        assertEquals(4L, event.id)
        assertEquals("session_waiting", event.type)
    }

    @Test
    fun parsesStreamSessionUpdate() {
        val message = ActivityJsonParser.parseStreamMessage(
            """
            {
              "event_type": "session_update",
              "data": {
                "provider_id": "codex",
                "project_path": "/work/codegauge",
                "state": "running",
                "last_activity_at": "2026-06-12T08:32:00Z",
                "last_event_type": "session_start"
              }
            }
            """.trimIndent(),
        )

        assertTrue(message is ActivityStreamMessage.Session)
        val session = (message as ActivityStreamMessage.Session).session
        assertEquals("codex", session.providerId)
        assertEquals("/work/codegauge", session.projectPath)
        assertEquals("running", session.state)
    }

    @Test
    fun parsesStreamAlert() {
        val message = ActivityJsonParser.parseStreamMessage(
            """
            {
              "event_type": "alert",
              "data": {
                "provider_id": "claude",
                "window_type": "5h",
                "severity": "warning",
                "threshold": 80,
                "usage_percent": 84,
                "quota_event_key": "claude:5h:warning"
              }
            }
            """.trimIndent(),
        )

        assertTrue(message is ActivityStreamMessage.Alert)
        val alert = (message as ActivityStreamMessage.Alert).alert
        assertEquals("claude", alert.providerId)
        assertEquals("warning", alert.severity)
        assertEquals(84, alert.usagePercent)
    }
}
