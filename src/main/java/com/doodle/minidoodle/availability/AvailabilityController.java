package com.doodle.minidoodle.availability;

import com.doodle.minidoodle.slot.SlotService;

import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
public class AvailabilityController {
    private final SlotService slots;

    public AvailabilityController(SlotService slots) {
        this.slots = slots;
    }

    @GetMapping("/users/{userId}/availability")
    public Availability.Summary get(
            @PathVariable UUID userId,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to) {
        return Availability.aggregate(
                from.toInstant(),
                to.toInstant(),
                slots.find(userId, from.toInstant(), to.toInstant(), null));
    }
}
