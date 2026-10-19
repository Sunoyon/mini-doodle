package com.doodle.minidoodle.slot;

import com.doodle.minidoodle.dtos.SlotResponse;
import com.doodle.minidoodle.models.Status;
import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/users/{userId}/slots")
public class SlotController {
    private final SlotService slots;

    public SlotController(SlotService slots) {
        this.slots = slots;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse create(
            @PathVariable UUID userId, @Valid @RequestBody SlotService.CreateSlot input) {
        return slots.create(userId, input);
    }

    @PatchMapping("/{slotId}")
    public SlotResponse update(
            @PathVariable UUID userId,
            @PathVariable UUID slotId,
            @Valid @RequestBody SlotService.UpdateSlot input) {
        return slots.update(userId, slotId, input);
    }

    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID userId, @PathVariable UUID slotId) {
        slots.delete(userId, slotId);
    }

    @GetMapping
    public List<SlotResponse> find(
            @PathVariable UUID userId,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) Status status) {
        return slots.find(userId, from.toInstant(), to.toInstant(), status);
    }
}
