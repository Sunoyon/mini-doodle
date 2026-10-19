package com.doodle.minidoodle.user;

import java.util.UUID;
import com.doodle.minidoodle.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
}
