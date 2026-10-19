package com.doodle.minidoodle.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUser(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email) {
}
