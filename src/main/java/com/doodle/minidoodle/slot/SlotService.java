package com.doodle.minidoodle.slot;

import com.doodle.minidoodle.config.ApiException;
import com.doodle.minidoodle.dtos.SlotResponse;
import com.doodle.minidoodle.models.Slot;
import com.doodle.minidoodle.models.Status;
import com.doodle.minidoodle.user.UserService;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SlotService {
    private final SlotRepository slots;
    private final UserService users;

    public SlotService(SlotRepository slots, UserService users) {
        this.slots = slots;
        this.users = users;
    }

    public record CreateSlot(@NotNull OffsetDateTime start, @NotNull OffsetDateTime end,
                             @NotNull Status status) {
    }

    public record UpdateSlot(OffsetDateTime start, OffsetDateTime end, Status status) {
    }

    public static void validate(Instant start, Instant end) {
        if (!start.isBefore(end)) throw ApiException.badRequest("start must be before end");
        if (start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0)
            throw ApiException.badRequest("Timestamps support at most microsecond precision");
    }

    public static void validateWindow(Instant from, Instant to) {
        validate(from, to);
        if (Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0)
            throw ApiException.badRequest("Query window cannot exceed 366 days");
    }

    @Transactional
    public SlotResponse create(UUID userId, CreateSlot input) {
        var calendar = users.calendar(userId);
        var start = input.start().toInstant();
        var end = input.end().toInstant();
        validate(start, end);
        return SlotResponse.from(slots.saveAndFlush(new Slot(calendar, start, end, input.status())));
    }

    @Transactional
    public SlotResponse update(UUID userId, UUID id, UpdateSlot input) {
        var slot = lock(userId, id);
        if (slots.hasMeeting(id))
            throw ApiException.conflict("Cancel the meeting before editing its slot");
        var start = input.start() == null ? slot.getStart() : input.start().toInstant();
        var end = input.end() == null ? slot.getEnd() : input.end().toInstant();
        var status = input.status() == null ? slot.getStatus() : input.status();
        validate(start, end);
        slot.update(start, end, status);
        slots.flush();
        return SlotResponse.from(slot);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        var slot = lock(userId, id);
        if (slots.hasMeeting(id))
            throw ApiException.conflict("Cancel the meeting before deleting its slot");
        slots.delete(slot);
        slots.flush();
    }

    public List<SlotResponse> find(UUID userId, Instant from, Instant to, Status status) {
        validateWindow(from, to);
        return slots.findOverlapping(users.calendar(userId).getId(), from, to).stream()
                .filter(slot -> status == null || slot.getStatus() == status)
                .map(SlotResponse::from).toList();
    }

    private Slot lock(UUID userId, UUID id) {
        return slots.lock(users.calendar(userId).getId(), id)
                .orElseThrow(() -> ApiException.notFound("Slot"));
    }
}
