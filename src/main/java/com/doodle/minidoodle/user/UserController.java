package com.doodle.minidoodle.user;

import java.net.URI;
import java.util.UUID;

import com.doodle.minidoodle.dtos.CreateUser;
import com.doodle.minidoodle.dtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUser request) {
        var user = users.create(request);
        return ResponseEntity.created(URI.create("/users/" + user.getId())).body(UserResponse.from(user));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.from(users.get(id));
    }
}
