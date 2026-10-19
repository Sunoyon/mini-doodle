package com.doodle.minidoodle.models;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "meeting")
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false, unique = true, updatable = false)
    private Slot slot;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingParticipant> participants = new ArrayList<>();

    protected Meeting() {
    }

    public Meeting(Slot slot, String title, String description, List<String> emails) {
        this.slot = slot;
        this.title = title;
        this.description = description;
        emails.forEach(email -> participants.add(new MeetingParticipant(this, email)));
    }

    public UUID getId() {
        return id;
    }

    public Slot getSlot() {
        return slot;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<MeetingParticipant> getParticipants() {
        return List.copyOf(participants);
    }
}
