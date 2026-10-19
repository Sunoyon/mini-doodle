package com.doodle.minidoodle.user;

import java.util.Locale;
import java.util.UUID;
import com.doodle.minidoodle.dtos.CreateUser;
import com.doodle.minidoodle.models.Calendar;
import com.doodle.minidoodle.models.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;
    private final CalendarRepository calendars;

    public UserService(UserRepository users, CalendarRepository calendars) {
        this.users = users;
        this.calendars = calendars;
    }

    @Transactional
    public User create(CreateUser request) {
        var user = new User(request.name().trim(), request.email().trim().toLowerCase(Locale.ROOT));
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered", ex);
        }
        calendars.save(new Calendar(user));
        return user;
    }

    public User get(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public Calendar calendar(UUID userId) {
        return calendars.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
