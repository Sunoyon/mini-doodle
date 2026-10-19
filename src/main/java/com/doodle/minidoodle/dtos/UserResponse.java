package com.doodle.minidoodle.dtos;

import java.util.UUID;
import com.doodle.minidoodle.models.User;

public record UserResponse(UUID id, String name, String email) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
