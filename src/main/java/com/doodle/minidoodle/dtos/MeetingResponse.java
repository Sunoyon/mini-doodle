package com.doodle.minidoodle.dtos;

import com.doodle.minidoodle.models.Meeting;
import com.doodle.minidoodle.models.MeetingParticipant;

import java.util.List;
import java.util.UUID;

public record MeetingResponse(UUID id, UUID slotId, String title, String description, List<String> participants) {
    public static MeetingResponse from(Meeting meeting) {
        return new MeetingResponse(meeting.getId(), meeting.getSlot().getId(), meeting.getTitle(),
                meeting.getDescription(), meeting.getParticipants().stream()
                .map(MeetingParticipant::getEmail).sorted().toList());
    }
}
