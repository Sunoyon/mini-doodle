package com.doodle.minidoodle.meeting;

import com.doodle.minidoodle.models.Meeting;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {
    @EntityGraph(attributePaths = "participants")
    @Query("select m from Meeting m where m.id = :id")
    Optional<Meeting> findWithParticipants(@Param("id") UUID id);

    interface Location {
        UUID getSlotId();

        UUID getCalendarId();
    }

    @Query("select m.slot.id as slotId, m.slot.calendar.id as calendarId from Meeting m where m.id = :id")
    Optional<Location> findLocation(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query(value = "delete from meeting where id = :id", nativeQuery = true)
    int deleteMeeting(@Param("id") UUID id);
}
