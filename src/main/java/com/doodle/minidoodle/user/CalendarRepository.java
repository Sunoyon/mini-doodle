package com.doodle.minidoodle.user;

import java.util.Optional;
import java.util.UUID;

import com.doodle.minidoodle.models.Calendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarRepository extends JpaRepository<Calendar, UUID> {
    @Query("select c from Calendar c where c.user.id = :userId")
    Optional<Calendar> findByUserId(@Param("userId") UUID userId);
}
