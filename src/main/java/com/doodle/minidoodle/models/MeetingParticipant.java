package com.doodle.minidoodle.models;

import jakarta.persistence.*;

@Entity
@Table(name = "meeting_participant")
public class MeetingParticipant {
    @EmbeddedId
    private MeetingParticipantId id;

    @MapsId("meetingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    protected MeetingParticipant() {
    }

    public MeetingParticipant(Meeting meeting, String email) {
        this.meeting = meeting;
        this.id = new MeetingParticipantId(meeting.getId(), email);
    }

    public MeetingParticipantId getId() {
        return id;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public String getEmail() {
        return id.getEmail();
    }
}
