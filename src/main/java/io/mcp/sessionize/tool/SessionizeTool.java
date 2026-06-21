package io.mcp.sessionize.tool;

import module java.base;

import io.mcp.sessionize.client.SessionizeClient;
import io.mcp.sessionize.model.SessionizeModel.*;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
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
        "Event ID is required. Provide the eventId parameter or set the SESSIONIZE_EVENT_ID environment variable. "
        + "Find the event ID in your Sessionize API/Embed URL: https://sessionize.com/api/v2/{EVENT_ID}/view/All";
    private static final String MARKDOWN_SEPARATOR = "\n---\n";

    /** Default page size for list tools when the caller does not specify one. */
    private static final int DEFAULT_LIMIT = 25;
    /** Hard cap on page size to keep responses within a reasonable token budget. */
    private static final int MAX_LIMIT = 100;

    @Inject
    @RestClient
    SessionizeClient client;

    @ConfigProperty(name = "sessionize.event-id")
    Optional<String> defaultEventId;

    // ========== SPEAKERS ==========

    @Tool(name = "sessionize_get_speakers",
            description = "List all speakers for a Sessionize event. " +
            "Returns speaker names, bios, taglines, and social links. " +
            "If eventId is not provided, uses the configured default event. " +
            "Results are paginated; use limit/offset for large events. " +
            "Use this when the user asks 'who are the speakers?' or 'show me all speakers'.",
            annotations = @Annotations(
                title = "List Speakers",
                readOnlyHint = true,
                destructiveHint = false,
                idempotentHint = true,
                openWorldHint = true))
    Uni<ToolResponse> getSpeakers(
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            @ToolArg(description = "Maximum number of speakers to return (default 25, max 100).",
                     defaultValue = "25")
            int limit,
            @ToolArg(description = "Number of speakers to skip, for pagination (default 0).",
                     defaultValue = "0")
            int offset,
            McpLog log) {

        return validateAndExecute(eventId, null, null, log, id -> {
            log.info("Fetching speakers for event: %s", id);
            var speakers = fetchSpeakers(id);

            return speakers.isEmpty()
                ? noResultsFor("speakers", id)
                : paginate(speakers, limit, offset, this::formatSpeaker, "speakers");
        });
    }

    @Tool(name = "sessionize_find_speaker",
            description = "Search for a speaker by name in a Sessionize event. " +
            "Returns matching speakers with full details including bio and social links. " +
            "Use this when the user asks about a specific speaker like 'find speaker John Doe' or 'tell me about speaker X'.",
            annotations = @Annotations(
                title = "Find Speaker",
                readOnlyHint = true,
                destructiveHint = false,
                idempotentHint = true,
                openWorldHint = true))
    Uni<ToolResponse> findSpeaker(
            @ToolArg(description = "Speaker name to search for")
            String name,
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            @ToolArg(description = "Maximum number of speakers to return (default 25, max 100).",
                     defaultValue = "25")
            int limit,
            @ToolArg(description = "Number of speakers to skip, for pagination (default 0).",
                     defaultValue = "0")
            int offset,
            McpLog log) {

        return validateAndExecute(eventId, name, "name", log, id -> {
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
                : paginate(matchingSpeakers, limit, offset, this::formatSpeakerDetailed, "speakers");
        });
    }

    @Tool(name = "sessionize_get_sessions_by_speaker",
            description = "Get all sessions for a specific speaker. " +
            "Returns the list of sessions the speaker is presenting. " +
            "Use this when the user asks 'what sessions does X have?' or 'what is speaker Y presenting?'.",
            annotations = @Annotations(
                title = "Sessions by Speaker",
                readOnlyHint = true,
                destructiveHint = false,
                idempotentHint = true,
                openWorldHint = true))
    Uni<ToolResponse> getSessionsBySpeaker(
            @ToolArg(description = "Speaker name to search for")
            String speakerName,
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            McpLog log) {

        return validateAndExecute(eventId, speakerName, "speakerName", log, id -> {
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

    @Tool(name = "sessionize_get_sessions",
            description = "List the confirmed, non-service sessions for a Sessionize event " +
            "(breaks, registration and other service slots are excluded). " +
            "Returns session titles, descriptions, speakers, and schedule information. " +
            "Results are paginated; use limit/offset for large events. " +
            "Use this when the user asks 'what sessions are available?' or 'show me all talks'.",
            annotations = @Annotations(
                title = "List Sessions",
                readOnlyHint = true,
                destructiveHint = false,
                idempotentHint = true,
                openWorldHint = true))
    Uni<ToolResponse> getSessions(
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            @ToolArg(description = "Maximum number of sessions to return (default 25, max 100).",
                     defaultValue = "25")
            int limit,
            @ToolArg(description = "Number of sessions to skip, for pagination (default 0).",
                     defaultValue = "0")
            int offset,
            McpLog log) {

        return validateAndExecute(eventId, null, null, log, id -> {
            log.info("Fetching sessions for event: %s", id);
            var sessions = fetchAllSessions(id);

            return sessions.isEmpty()
                ? noResultsFor("sessions", id)
                : paginate(sessions, limit, offset, this::formatSession, "sessions");
        });
    }

    @Tool(name = "sessionize_find_session",
            description = "Search confirmed, non-service sessions by title or description. " +
            "Returns matching sessions with full details. " +
            "Results are paginated; use limit/offset for broad queries. " +
            "Use this when the user asks to find sessions about a topic like 'find sessions about Kubernetes' or 'sessions on AI'.",
            annotations = @Annotations(
                title = "Find Session",
                readOnlyHint = true,
                destructiveHint = false,
                idempotentHint = true,
                openWorldHint = true))
    Uni<ToolResponse> findSession(
            @ToolArg(description = "Text to search in session titles and descriptions")
            String query,
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            @ToolArg(description = "Maximum number of sessions to return (default 25, max 100).",
                     defaultValue = "25")
            int limit,
            @ToolArg(description = "Number of sessions to skip, for pagination (default 0).",
                     defaultValue = "0")
            int offset,
            McpLog log) {

        return validateAndExecute(eventId, query, "query", log, id -> {
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
                : paginate(matchingSessions, limit, offset, this::formatSession, "sessions");
        });
    }

    // ========== SCHEDULE ==========

    @Tool(name = "sessionize_get_schedule",
            description = "Get the event schedule/agenda from Sessionize. " +
            "Returns the schedule organized by day and time slot. " +
            "Note: Schedule may be empty if the event hasn't configured session times. " +
            "Use this when the user asks 'what's the schedule?' or 'show me the agenda'.",
            annotations = @Annotations(
                title = "Event Schedule",
                readOnlyHint = true,
                destructiveHint = false,
                idempotentHint = true,
                openWorldHint = true))
    Uni<ToolResponse> getSchedule(
            @ToolArg(description = "Sessionize event ID. Optional if SESSIONIZE_EVENT_ID is set.",
                     defaultValue = "")
            String eventId,
            McpLog log) {

        return validateAndExecute(eventId, null, null, log, id -> {
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
                                                  String requiredParamName, McpLog log, ToolAction action) {
        String id = resolveEventId(eventId);

        if (id.isEmpty()) {
            return Uni.createFrom().item(ToolResponse.error(EVENT_ID_REQUIRED));
        }

        if (requiredParam != null && isBlank(requiredParam)) {
            return Uni.createFrom().item(ToolResponse.error(
                "The '%s' parameter is required and must not be blank.".formatted(requiredParamName)));
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
                return ToolResponse.error(friendlyError(e));
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
     * Formats a page of items as Markdown and appends pagination metadata.
     * Clamps {@code limit} to [1, MAX_LIMIT] and {@code offset} to >= 0, so callers
     * never load an unbounded result set into a single response.
     */
    private <T> ToolResponse paginate(List<T> items, int limit, int offset,
                                      Function<T, String> formatter, String itemType) {
        int total = items.size();
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        if (safeOffset >= total) {
            return ToolResponse.success(new TextContent(
                "No more %s. Total is %d; offset %d is past the end.".formatted(itemType, total, safeOffset)));
        }

        int end = Math.min(safeOffset + safeLimit, total);
        String body = items.subList(safeOffset, end).stream()
            .map(formatter)
            .collect(Collectors.joining(MARKDOWN_SEPARATOR));

        boolean hasMore = end < total;
        String footer = MARKDOWN_SEPARATOR + "_Showing %d-%d of %d %s.%s_".formatted(
            safeOffset + 1, end, total, itemType,
            hasMore ? " Pass offset=%d to get the next page.".formatted(end) : "");

        return ToolResponse.success(new TextContent(body + footer));
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
     * Fetches confirmed sessions for an event, flattening session groups with null safety.
     * Only returns sessions that are confirmed and not service sessions (breaks, registration, etc.).
     */
    private List<Session> fetchAllSessions(String eventId) {
        var sessionGroups = client.getSessions(eventId);
        if (sessionGroups == null) return List.of();

        return sessionGroups.stream()
            .filter(Objects::nonNull)
            .filter(group -> group.sessions() != null)
            .flatMap(group -> group.sessions().stream())
            .filter(Objects::nonNull)
            .filter(Session::isConfirmed)
            .filter(session -> !session.isServiceSession())
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
     * Maps an unexpected (non-HTTP-status) exception to a safe, actionable message.
     * <p>
     * The most common real-world failure is a 200 response with {@code text/html} instead of
     * JSON, which happens when the event exists but its JSON API/Embed is not enabled. The raw
     * mapping error leaks internal type names, so it is never returned to the client.
     */
    private String friendlyError(Exception e) {
        String detail = e.getMessage() == null ? "" : e.getMessage();
        boolean nonJsonResponse = detail.contains("could not be mapped") || detail.contains("text/html");
        if (nonJsonResponse) {
            return "The Sessionize API returned a non-JSON response, which usually means the event's "
                + "JSON API is not enabled. Enable it under Sessionize -> API/Embed and verify the eventId.";
        }
        return "Unexpected error while contacting Sessionize. Please retry; if it persists, check the event configuration.";
    }

    /**
     * Extracts a user-friendly error message from API exceptions.
     */
    private String extractErrorMessage(WebApplicationException e) {
        if (e.getResponse() == null) {
            return e.getMessage();
        }
        return switch (e.getResponse().getStatus()) {
            case 404 -> "Event not found. Verify the eventId — copy it from your Sessionize API/Embed URL "
                    + "(https://sessionize.com/api/v2/{EVENT_ID}/view/All) and ensure the API is enabled for the event.";
            case 403 -> "Access denied. Enable the public API for this event under Sessionize → API/Embed.";
            case 429 -> "Rate limit exceeded. Wait a few seconds and retry.";
            case 500, 502, 503 -> "Sessionize service temporarily unavailable. Retry shortly.";
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
