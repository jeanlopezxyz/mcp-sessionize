package io.mcp.sessionize.prompt;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;

/**
 * MCP Prompts for Sessionize Event Queries.
 * <p>
 * Provides prompt templates that guide AI agents and users on how to
 * interact with Sessionize event data effectively.
 * <p>
 * Note: {@code @Singleton} scope is automatically added by Quarkus MCP Server.
 */
public class SessionizePrompt {

    @Prompt(name = "event_overview",
            description = "Get an overview of a conference event including speakers and sessions count")
    PromptMessage eventOverview(
            @PromptArg(name = "eventId",
                       description = "Sessionize event ID (optional if default is configured)",
                       defaultValue = "")
            String eventId) {
        String prompt = eventId.isBlank()
            ? "Give me an overview of the conference. List how many speakers and sessions there are, and highlight any notable topics."
            : "Give me an overview of the conference with event ID '%s'. List how many speakers and sessions there are, and highlight any notable topics.".formatted(eventId);
        return PromptMessage.withUserRole(new TextContent(prompt));
    }

    @Prompt(name = "find_speaker_info",
            description = "Find detailed information about a specific speaker")
    PromptMessage findSpeakerInfo(
            @PromptArg(name = "speakerName",
                       description = "Name of the speaker to search for")
            String speakerName) {
        String prompt = "Find information about the speaker '%s'. Include their bio, tagline, social links, and what sessions they are presenting.".formatted(speakerName);
        return PromptMessage.withUserRole(new TextContent(prompt));
    }

    @Prompt(name = "sessions_by_topic",
            description = "Find all sessions related to a specific topic or technology")
    PromptMessage sessionsByTopic(
            @PromptArg(name = "topic",
                       description = "Topic or technology to search for (e.g., Kubernetes, AI, Java)")
            String topic) {
        String prompt = "Find all sessions about '%s'. For each session, show the title, speakers, time, and a brief description.".formatted(topic);
        return PromptMessage.withUserRole(new TextContent(prompt));
    }

    @Prompt(name = "conference_schedule",
            description = "Get the full conference schedule organized by day and time")
    PromptMessage conferenceSchedule(
            @PromptArg(name = "day",
                       description = "Specific day to filter (optional, leave empty for full schedule)",
                       defaultValue = "")
            String day) {
        String prompt = day.isBlank()
            ? "Show me the full conference schedule organized by day and time slot. Include room information and session titles."
            : "Show me the conference schedule for %s. Include room information, time slots, and session titles.".formatted(day);
        return PromptMessage.withUserRole(new TextContent(prompt));
    }

    @Prompt(name = "speaker_sessions",
            description = "List all sessions being presented by a specific speaker")
    PromptMessage speakerSessions(
            @PromptArg(name = "speakerName",
                       description = "Name of the speaker")
            String speakerName) {
        String prompt = "What sessions is '%s' presenting at the conference? Include the session titles, times, and rooms.".formatted(speakerName);
        return PromptMessage.withUserRole(new TextContent(prompt));
    }

    @Prompt(name = "recommend_sessions",
            description = "Get session recommendations based on interests")
    PromptMessage recommendSessions(
            @PromptArg(name = "interests",
                       description = "Your interests or technologies (e.g., 'cloud native, security, DevOps')")
            String interests) {
        String prompt = "Based on my interests in %s, recommend sessions I should attend at this conference. Explain why each session would be relevant.".formatted(interests);
        return PromptMessage.withUserRole(new TextContent(prompt));
    }
}
