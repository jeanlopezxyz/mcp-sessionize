package io.mcp.sessionize.tool;

import module java.base;

import io.mcp.sessionize.client.SessionizeClient;
import io.mcp.sessionize.model.SessionizeModel.*;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * MCP Tools for Sessionize Event Data.
 * <p>
 * Provides tools to access any Sessionize event:
 * <ul>
 *   <li>getSpeakers - List all speakers</li>
 *   <li>findSpeaker - Search speakers by name</li>
 *   <li>getSessionsBySpeaker - Get sessions for a speaker</li>
 *   <li>getSessions - List all sessions</li>
 *   <li>findSession - Search sessions</li>
 *   <li>getSchedule - Get event schedule</li>
 * </ul>
 * <p>
 * Note: {@code @Singleton} scope is automatically added by Quarkus MCP Server.
 */
public class SessionizeTool {

    private static final Logger LOG = Logger.getLogger(SessionizeTool.class);
    private static final String EVENT_ID_REQUIRED =
        "Event ID is required. Provide eventId parameter or set SESSIONIZE_EVENT_ID environment variable.";
    private static final String MARKDOWN_SEPARATOR = "\n---\n";

    @Inject
    @RestClient
    SessionizeClient client;

    @ConfigProperty(name = "sessionize.event-id")
    Optional<String> defaultEventId;

    // ========== SPEAKERS ==========

    @Tool(description = "List all speakers for a Sessionize event. " +
            "Returns speaker names, bios, taglines, and social links. " +
            "If eventId is not provided, uses the configured default event. " +
            "Use this when the user asks 'who are the speakers?' or 'show me all speakers'.")
    Uni<ToolResponse> getSpeakers(
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            McpLog log) {

        return validateAndExecute(eventId, null, log, id -> {
            log.info("Fetching speakers for event: %s", id);
            var speakers = fetchSpeakers(id);

            return speakers.isEmpty()
                ? noResultsFor("speakers", id)
                : successResponse(speakers, this::formatSpeaker);
        });
    }

    @Tool(description = "Search for a speaker by name in a Sessionize event. " +
            "Returns matching speakers with full details including bio and social links. " +
            "Use this when the user asks about a specific speaker like 'find speaker John Doe' or 'tell me about speaker X'.")
    Uni<ToolResponse> findSpeaker(
            @ToolArg(description = "Speaker name to search for")
            String name,
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            McpLog log) {

        return validateAndExecute(eventId, name, log, id -> {
            log.info("Searching speaker '%s' in event: %s", name, id);
            var allSpeakers = fetchSpeakers(id);

            if (allSpeakers.isEmpty()) {
                return noResultsFor("speakers", id);
            }

            var matchingSpeakers = allSpeakers.stream()
                .filter(speaker -> containsIgnoreCase(speaker.fullName(), name))
                .toList();

            return matchingSpeakers.isEmpty()
                ? noResultsMatching("speakers", name)
                : successResponse(matchingSpeakers, this::formatSpeakerDetailed);
        });
    }

    @Tool(description = "Get all sessions for a specific speaker. " +
            "Returns the list of sessions the speaker is presenting. " +
            "Use this when the user asks 'what sessions does X have?' or 'what is speaker Y presenting?'.")
    Uni<ToolResponse> getSessionsBySpeaker(
            @ToolArg(description = "Speaker name to search for")
            String speakerName,
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            McpLog log) {

        return validateAndExecute(eventId, speakerName, log, id -> {
            log.info("Getting sessions for speaker '%s' in event: %s", speakerName, id);
            var allSpeakers = fetchSpeakers(id);

            if (allSpeakers.isEmpty()) {
                return noResultsFor("speakers", id);
            }

            return allSpeakers.stream()
                .filter(s -> containsIgnoreCase(s.fullName(), speakerName))
                .findFirst()
                .map(this::formatSpeakerSessions)
                .orElseGet(() -> noResultsMatching("speaker", speakerName));
        });
    }

    // ========== SESSIONS ==========

    @Tool(description = "List all sessions for a Sessionize event. " +
            "Returns session titles, descriptions, speakers, and schedule information. " +
            "Use this when the user asks 'what sessions are available?' or 'show me all talks'.")
    Uni<ToolResponse> getSessions(
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            McpLog log) {

        return validateAndExecute(eventId, null, log, id -> {
            log.info("Fetching sessions for event: %s", id);
            var sessions = fetchAllSessions(id);

            return sessions.isEmpty()
                ? noResultsFor("sessions", id)
                : successResponse(sessions, this::formatSession);
        });
    }

    @Tool(description = "Search sessions by title or description. " +
            "Returns matching sessions with full details. " +
            "Use this when the user asks to find sessions about a topic like 'find sessions about Kubernetes' or 'sessions on AI'.")
    Uni<ToolResponse> findSession(
            @ToolArg(description = "Text to search in session titles and descriptions")
            String query,
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            McpLog log) {

        return validateAndExecute(eventId, query, log, id -> {
            log.info("Searching sessions for '%s' in event: %s", query, id);
            var allSessions = fetchAllSessions(id);

            if (allSessions.isEmpty()) {
                return noResultsFor("sessions", id);
            }

            var matchingSessions = allSessions.stream()
                .filter(session -> containsIgnoreCase(session.title(), query) ||
                                   containsIgnoreCase(session.description(), query))
                .toList();

            return matchingSessions.isEmpty()
                ? noResultsMatching("sessions", query)
                : successResponse(matchingSessions, this::formatSession);
        });
    }

    // ========== SCHEDULE ==========

    @Tool(description = "Get the event schedule/agenda from Sessionize. " +
            "Returns the schedule organized by day and time slot. " +
            "Note: Schedule may be empty if the event hasn't configured session times. " +
            "Use this when the user asks 'what's the schedule?' or 'show me the agenda'.")
    Uni<ToolResponse> getSchedule(
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            McpLog log) {

        return validateAndExecute(eventId, null, log, id -> {
            log.info("Fetching schedule for event: %s", id);
            var schedule = client.getSchedule(id);

            if (schedule == null || schedule.isEmpty()) {
                return ToolResponse.success(new TextContent("""
                    No schedule configured for this event yet.
                    The event organizer may not have assigned times to sessions."""));
            }

            return ToolResponse.success(new TextContent(formatSchedule(schedule)));
        });
    }

    // ========== PRIVATE HELPERS ==========

    /**
     * Validates inputs and executes the tool action with proper error handling.
     * Uses McpLog to send notifications to connected MCP clients.
     */
    private Uni<ToolResponse> validateAndExecute(String eventId, String requiredParam,
                                                  McpLog log, ToolAction action) {
        String id = resolveEventId(eventId);

        if (id.isEmpty()) {
            return Uni.createFrom().item(ToolResponse.error(EVENT_ID_REQUIRED));
        }

        if (requiredParam != null && isBlank(requiredParam)) {
            return Uni.createFrom().item(ToolResponse.error("Required parameter is missing."));
        }

        return Uni.createFrom().item(() -> {
            try {
                return action.execute(id);
            } catch (WebApplicationException e) {
                LOG.warnf("API error for event %s: %s", id, e.getMessage());
                log.info("API error: %s", extractErrorMessage(e));
                return ToolResponse.error("Sessionize API error: " + extractErrorMessage(e));
            } catch (Exception e) {
                LOG.errorf(e, "Unexpected error for event: %s", id);
                log.info("Error processing request for event: %s", id);
                return ToolResponse.error("Error: " + e.getMessage());
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @FunctionalInterface
    private interface ToolAction {
        ToolResponse execute(String eventId);
    }

    // ========== RESPONSE HELPERS ==========

    /**
     * Creates a "no results found for event" response.
     */
    private ToolResponse noResultsFor(String itemType, String eventId) {
        return ToolResponse.success(new TextContent("No %s found for event: %s".formatted(itemType, eventId)));
    }

    /**
     * Creates a "no results matching query" response.
     */
    private ToolResponse noResultsMatching(String itemType, String query) {
        return ToolResponse.success(new TextContent("No %s found matching: %s".formatted(itemType, query)));
    }

    /**
     * Formats a list of items and wraps in a success response.
     * Eliminates duplication of stream().map().collect() pattern.
     */
    private <T> ToolResponse successResponse(List<T> items, Function<T, String> formatter) {
        String result = items.stream()
            .map(formatter)
            .collect(Collectors.joining(MARKDOWN_SEPARATOR));
        return ToolResponse.success(new TextContent(result));
    }

    // ========== DATA FETCHERS ==========

    /**
     * Fetches all speakers for an event with null safety.
     */
    private List<Speaker> fetchSpeakers(String eventId) {
        var speakers = client.getSpeakers(eventId);
        return speakers != null ? speakers : List.of();
    }

    /**
     * Fetches all sessions for an event, flattening session groups with null safety.
     */
    private List<Session> fetchAllSessions(String eventId) {
        var sessionGroups = client.getSessions(eventId);
        if (sessionGroups == null) return List.of();

        return sessionGroups.stream()
            .filter(Objects::nonNull)
            .filter(group -> group.sessions() != null)
            .flatMap(group -> group.sessions().stream())
            .filter(Objects::nonNull)
            .toList();
    }

    // ========== VALIDATION HELPERS ==========

    /**
     * Resolves the event ID from the provided value or falls back to the default.
     */
    private String resolveEventId(String providedId) {
        if (!isBlank(providedId)) {
            return sanitizeEventId(providedId);
        }
        return defaultEventId.filter(id -> !isBlank(id)).map(this::sanitizeEventId).orElse("");
    }

    /**
     * Sanitizes event ID to prevent injection attacks.
     */
    private String sanitizeEventId(String eventId) {
        return eventId.replaceAll("[^a-zA-Z0-9]", "");
    }

    /**
     * Checks if a string is null or blank.
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Case-insensitive substring search.
     */
    private boolean containsIgnoreCase(String text, String search) {
        return text != null && search != null &&
               text.toLowerCase().contains(search.toLowerCase());
    }

    /**
     * Extracts a user-friendly error message from API exceptions.
     */
    private String extractErrorMessage(WebApplicationException e) {
        if (e.getResponse() == null) {
            return e.getMessage();
        }
        return switch (e.getResponse().getStatus()) {
            case 404 -> "Event not found";
            case 403 -> "Access denied";
            case 429 -> "Rate limit exceeded";
            case 500, 502, 503 -> "Sessionize service unavailable";
            default -> e.getMessage();
        };
    }

    /**
     * Returns the string or "Unknown" if null/blank.
     */
    private String safeString(String value) {
        return isBlank(value) ? "Unknown" : value;
    }

    // ========== FORMATTERS ==========

    /**
     * Formats a speaker as markdown with basic info.
     */
    private String formatSpeaker(Speaker speaker) {
        if (speaker == null) return "";
        var output = new StringBuilder();
        output.append("## ").append(safeString(speaker.fullName())).append("\n");
        if (!isBlank(speaker.tagLine())) {
            output.append("*").append(speaker.tagLine()).append("*\n");
        }
        if (!isBlank(speaker.bio())) {
            output.append(speaker.bio()).append("\n");
        }
        return output.toString();
    }

    /**
     * Formats a speaker as markdown with full details including links and sessions.
     */
    private String formatSpeakerDetailed(Speaker speaker) {
        if (speaker == null) return "";
        var output = new StringBuilder(formatSpeaker(speaker));
        if (speaker.links() != null && !speaker.links().isEmpty()) {
            output.append("\n**Links:**\n");
            speaker.links().stream()
                .filter(Objects::nonNull)
                .forEach(link ->
                    output.append("- ").append(safeString(link.title())).append(": ")
                          .append(safeString(link.url())).append("\n"));
        }
        if (speaker.sessions() != null && !speaker.sessions().isEmpty()) {
            output.append("\n**Sessions:**\n");
            speaker.sessions().stream()
                .filter(Objects::nonNull)
                .forEach(sessionRef ->
                    output.append("- ").append(safeString(sessionRef.name())).append("\n"));
        }
        return output.toString();
    }

    /**
     * Formats a speaker's sessions as a ToolResponse.
     */
    private ToolResponse formatSpeakerSessions(Speaker speaker) {
        var output = new StringBuilder();
        output.append("## Sessions by ").append(safeString(speaker.fullName())).append("\n\n");

        if (speaker.sessions() != null && !speaker.sessions().isEmpty()) {
            speaker.sessions().stream()
                .filter(Objects::nonNull)
                .forEach(ref -> output.append("- ").append(safeString(ref.name())).append("\n"));
        } else {
            output.append("No sessions assigned.\n");
        }

        return ToolResponse.success(new TextContent(output.toString()));
    }

    /**
     * Formats a session as markdown with title, time, room, speakers, and description.
     */
    private String formatSession(Session session) {
        if (session == null) return "";
        var output = new StringBuilder();
        output.append("## ").append(safeString(session.title())).append("\n");

        if (session.startsAt() != null) {
            output.append("**Time:** ").append(session.startsAt());
            if (session.endsAt() != null) {
                output.append(" - ").append(session.endsAt());
            }
            output.append("\n");
        }

        if (!isBlank(session.room())) {
            output.append("**Room:** ").append(session.room()).append("\n");
        }

        if (session.speakers() != null && !session.speakers().isEmpty()) {
            output.append("**Speakers:** ").append(
                session.speakers().stream()
                    .filter(Objects::nonNull)
                    .map(speakerRef -> safeString(speakerRef.name()))
                    .collect(Collectors.joining(", "))
            ).append("\n");
        }

        output.append("\n");
        output.append(session.description() != null ? session.description() : "No description available.");

        return output.toString();
    }

    /**
     * Formats the schedule as markdown organized by day and time slot.
     */
    private String formatSchedule(List<GridDay> schedule) {
        var output = new StringBuilder();
        output.append("# Event Schedule\n\n");

        for (var day : schedule) {
            if (day == null) continue;
            output.append("## ").append(safeString(day.date())).append("\n\n");
            if (day.timeSlots() != null) {
                for (var timeSlot : day.timeSlots()) {
                    if (timeSlot == null) continue;
                    output.append("### ").append(safeString(timeSlot.slotStart())).append("\n");
                    if (timeSlot.rooms() != null) {
                        for (var room : timeSlot.rooms()) {
                            if (room == null || room.session() == null) continue;
                            output.append("- **").append(safeString(room.name())).append("**: ")
                                  .append(safeString(room.session().title())).append("\n");
                        }
                    }
                    output.append("\n");
                }
            }
        }

        return output.toString();
    }
}
