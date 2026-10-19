package com.doodle.minidoodle.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MeetingParticipantId implements Serializable {
    @Column(name = "meeting_id", nullable = false)
    private UUID meetingId;

    @Column(nullable = false, length = 254)
    private String email;

    protected MeetingParticipantId() {
    }

    public MeetingParticipantId(UUID meetingId, String email) {
        this.meetingId = meetingId;
        this.email = email;
    }

    public UUID getMeetingId() {
        return meetingId;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MeetingParticipantId that)) return false;
        return Objects.equals(meetingId, that.meetingId) && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meetingId, email);
    }
}
