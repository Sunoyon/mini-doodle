package com.doodle.minidoodle.models;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot")
public class Slot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(name = "starts_at", nullable = false)
    private Instant start;

    @Column(name = "ends_at", nullable = false)
    private Instant end;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Status status;

    protected Slot() {
    }

    public Slot(Calendar calendar, Instant start, Instant end, Status status) {
        this.calendar = calendar;
        update(start, end, status);
    }

    public void update(Instant start, Instant end, Status status) {
        this.start = start;
        this.end = end;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }
}
