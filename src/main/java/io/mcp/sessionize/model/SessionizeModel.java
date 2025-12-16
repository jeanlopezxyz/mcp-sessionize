package io.mcp.sessionize.model;

import module java.base;

/**
 * Data models for Sessionize API responses.
 * All models are immutable records for thread safety and simplicity.
 *
 * @see <a href="https://sessionize.com/api-documentation">Sessionize API Documentation</a>
 */
public final class SessionizeModel {

    private SessionizeModel() {
    }

    // ========== Speaker Models ==========

    public record Speaker(
        String id,
        String fullName,
        String bio,
        String tagLine,
        String profilePicture,
        List<SessionRef> sessions,
        List<Link> links
    ) {}

    public record SessionRef(String id, String name) {}

    public record Link(String title, String url, String linkType) {}

    // ========== Session Models ==========

    public record SessionGroup(String groupName, List<Session> sessions) {}

    public record Session(
        String id,
        String title,
        String description,
        String startsAt,
        String endsAt,
        String room,
        List<SpeakerRef> speakers,
        List<Category> categories
    ) {}

    public record SpeakerRef(String id, String name) {}

    public record Category(int id, String name, List<CategoryItem> categoryItems) {}

    public record CategoryItem(int id, String name) {}

    // ========== Schedule Models ==========

    public record GridDay(String date, List<TimeSlot> timeSlots) {}

    public record TimeSlot(String slotStart, List<Room> rooms) {}

    public record Room(String name, RoomSession session) {}

    public record RoomSession(String title, List<SpeakerRef> speakers) {}
}
