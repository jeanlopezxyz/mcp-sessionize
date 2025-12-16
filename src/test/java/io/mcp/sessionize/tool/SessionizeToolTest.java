package io.mcp.sessionize.tool;

import module java.base;

import io.quarkiverse.mcp.server.McpLog;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SessionizeToolTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    SessionizeTool tool;

    /**
     * Test-only McpLog implementation that does nothing.
     */
    private static final McpLog NOOP_LOG = new McpLog() {
        @Override public McpLog.LogLevel level() { return McpLog.LogLevel.INFO; }
        @Override public void send(McpLog.LogLevel level, Object data) {}
        @Override public void send(McpLog.LogLevel level, String message, Object... args) {}
        @Override public void info(String message, Object... args) {}
        @Override public void debug(String message, Object... args) {}
        @Override public void error(String message, Object... args) {}
        @Override public void error(Throwable t, String message, Object... args) {}
    };

    @Test
    void testGetSpeakersWithoutEventId() {
        var response = tool.getSpeakers("", NOOP_LOG).await().atMost(TIMEOUT);
        assertNotNull(response);
    }

    @Test
    void testFindSpeakerWithEmptyName() {
        var response = tool.findSpeaker("", "test-event", NOOP_LOG).await().atMost(TIMEOUT);
        assertNotNull(response);
        assertTrue(response.isError());
    }

    @Test
    void testGetSessionsWithoutEventId() {
        var response = tool.getSessions("", NOOP_LOG).await().atMost(TIMEOUT);
        assertNotNull(response);
    }

    @Test
    void testFindSessionWithEmptyQuery() {
        var response = tool.findSession("", "test-event", NOOP_LOG).await().atMost(TIMEOUT);
        assertNotNull(response);
        assertTrue(response.isError());
    }

    @Test
    void testGetScheduleWithoutEventId() {
        var response = tool.getSchedule("", NOOP_LOG).await().atMost(TIMEOUT);
        assertNotNull(response);
    }

    @Test
    void testGetSessionsBySpeakerWithEmptyName() {
        var response = tool.getSessionsBySpeaker("", "test-event", NOOP_LOG).await().atMost(TIMEOUT);
        assertNotNull(response);
        assertTrue(response.isError());
    }
}
