package com.doodle.minidoodle.dtos;

import jakarta.validation.constraints.*;

import java.util.List;

public record CreateMeeting(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @NotNull @Size(max = 100) List<@NotBlank @Email @Size(max = 254) String> participants) {
}
