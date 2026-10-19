package com.doodle.minidoodle.meeting;

import com.doodle.minidoodle.dtos.CreateMeeting;
import com.doodle.minidoodle.dtos.MeetingResponse;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
public class MeetingController {
    private final MeetingService meetings;

    public MeetingController(MeetingService meetings) {
        this.meetings = meetings;
    }

    @PostMapping("/users/{userId}/slots/{slotId}/meeting")
    public ResponseEntity<MeetingResponse> book(
            @PathVariable UUID userId,
            @PathVariable UUID slotId,
            @Valid @RequestBody CreateMeeting input) {
        var meeting = meetings.book(userId, slotId, input);
        return ResponseEntity.created(URI.create("/meetings/" + meeting.id())).body(meeting);
    }

    @GetMapping("/meetings/{id}")
    public MeetingResponse get(@PathVariable UUID id) {
        return meetings.get(id);
    }

    @DeleteMapping("/meetings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        meetings.cancel(id);
    }
}
