package com.doodle.minidoodle.dtos;

import com.doodle.minidoodle.models.Slot;
import com.doodle.minidoodle.models.Status;

import java.time.Instant;
import java.util.UUID;

public record SlotResponse(UUID id, Instant start, Instant end, Status status) {
    public static SlotResponse from(Slot slot) {
        return new SlotResponse(slot.getId(), slot.getStart(), slot.getEnd(), slot.getStatus());
    }
}
