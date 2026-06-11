package com.codegauge.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DashboardJsonParserTest {
    @Test
    fun parsesStatusSnapshotWithNullableQuotaFields() {
        val snapshot = DashboardJsonParser.parseStatus(
            """
            {
              "providers": [
                {
                  "id": "claude",
                  "name": "Claude",
                  "plan_tier": "",
                  "available": true,
                  "windows": [
                    {
                      "window_type": "5h",
                      "percent_left": null,
                      "used": 22445145,
                      "limit": null,
                      "resets_at": "2026-06-11T10:00:00Z",
                      "source": "ccusage",
                      "updated_at": "2026-06-11T06:17:05.637556Z"
                    }
                  ]
                },
                {
                  "id": "codex",
                  "name": "Codex",
                  "plan_tier": "",
                  "available": false,
                  "windows": []
                }
              ],
              "sessions": [
                {
                  "provider_id": "claude",
                  "project_path": "/work/codegauge",
                  "state": "running",
                  "last_activity_at": "2026-06-11T06:20:00Z"
                }
              ],
              "server_time": "2026-06-11T06:21:00Z"
            }
            """.trimIndent(),
        )

        assertEquals(2, snapshot.providers.size)
        assertEquals(Instant.parse("2026-06-11T06:21:00Z"), snapshot.serverTime)

        val claude = snapshot.providers.first()
        assertEquals("claude", claude.id)
        assertTrue(claude.available)
        assertEquals(1, claude.windows.size)
        assertNull(claude.windows.first().percentLeft)
        assertEquals(22_445_145L, claude.windows.first().used)
        assertNull(claude.windows.first().limit)
        assertEquals(Instant.parse("2026-06-11T10:00:00Z"), claude.windows.first().resetsAt)
        assertEquals("ccusage", claude.windows.first().source)

        val codex = snapshot.providers[1]
        assertFalse(codex.available)
        assertEquals(emptyList<QuotaWindowStatus>(), codex.windows)

        val session = snapshot.sessions.first()
        assertEquals("claude", session.providerId)
        assertEquals("/work/codegauge", session.projectPath)
        assertEquals("running", session.state)
    }
}

