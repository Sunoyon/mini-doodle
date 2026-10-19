package com.doodle.minidoodle.slot;

import com.doodle.minidoodle.models.Slot;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.calendar.id = :calendarId and s.id = :id")
    Optional<Slot> lock(@Param("calendarId") UUID calendarId, @Param("id") UUID id);

    @Query("""
            select s from Slot s where s.calendar.id = :calendarId
            and s.start < :to and s.end > :from
            order by s.start, s.id
            """)
    List<Slot> findOverlapping(@Param("calendarId") UUID calendarId,
                              @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "select exists(select 1 from meeting where slot_id = :slotId)", nativeQuery = true)
    boolean hasMeeting(@Param("slotId") UUID slotId);
}
