package com.doodle.minidoodle.meeting;

import com.doodle.minidoodle.config.ApiException;
import com.doodle.minidoodle.dtos.CreateMeeting;
import com.doodle.minidoodle.dtos.MeetingResponse;
import com.doodle.minidoodle.models.Meeting;
import com.doodle.minidoodle.models.Status;
import com.doodle.minidoodle.slot.SlotRepository;
import com.doodle.minidoodle.user.UserService;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class MeetingService {
    private final MeetingRepository meetings;
    private final SlotRepository slots;
    private final UserService users;

    public MeetingService(MeetingRepository meetings, SlotRepository slots, UserService users) {
        this.meetings = meetings;
        this.slots = slots;
        this.users = users;
    }

    @Transactional
    public MeetingResponse book(UUID userId, UUID slotId, CreateMeeting request) {
        var slot = slots.lock(users.calendar(userId).getId(), slotId)
                .orElseThrow(() -> ApiException.notFound("Slot"));
        if (slot.getStatus() != Status.FREE || slots.hasMeeting(slotId))
            throw ApiException.conflict("Slot is not free");
        var participants = request.participants().stream()
                .map(email -> email.trim().toLowerCase(Locale.ROOT)).distinct().sorted().toList();
        var meeting = meetings.saveAndFlush(new Meeting(slot, request.title().trim(),
                request.description() == null ? "" : request.description(), participants));
        slot.setStatus(Status.BUSY);
        return MeetingResponse.from(meeting);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MeetingResponse get(UUID id) {
        return MeetingResponse.from(meetings.findWithParticipants(id)
                .orElseThrow(() -> ApiException.notFound("Meeting")));
    }

    @Transactional
    public void cancel(UUID id) {
        var location = meetings.findLocation(id).orElseThrow(() -> ApiException.notFound("Meeting"));
        var slot = slots.lock(location.getCalendarId(), location.getSlotId())
                .orElseThrow(() -> ApiException.notFound("Meeting"));
        if (meetings.deleteMeeting(id) == 0) throw ApiException.notFound("Meeting");
        slot.setStatus(Status.FREE);
    }
}
