package com.doodle.minidoodle.availability;

import com.doodle.minidoodle.dtos.CreateUser;
import com.doodle.minidoodle.models.Status;
import com.doodle.minidoodle.slot.SlotService;
import com.doodle.minidoodle.user.UserService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AvailabilityControllerTests {

	@Value("${local.server.port}")
	int port;

	@Autowired
	UserService users;

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

    @Autowired
    SlotService slots;

    private UUID createUser() {
        return users.create(new CreateUser("Alice", UUID.randomUUID() + "@example.com")).getId();
    }

    private void slot(UUID user, String start, String end, Status status) {
        slots.create(user, new SlotService.CreateSlot(OffsetDateTime.parse(start), OffsetDateTime.parse(end), status));
    }

    private Availability.Summary get(UUID user, String from, String to) {
        return RestClient.create("http://localhost:" + port).get()
                .uri(builder -> builder.path("/users/{user}/availability")
                        .queryParam("from", from).queryParam("to", to).build(user))
                .retrieve().body(Availability.Summary.class);
    }

    private Availability.Interval interval(String start, String end, Availability.State state) {
        return new Availability.Interval(Instant.parse(start), Instant.parse(end), state);
    }

    @Test
    void clipsMergesAndFillsGaps() {
        var user = createUser();
        slot(user, "2026-11-01T08:00:00Z", "2026-11-01T10:00:00Z", Status.FREE);
        slot(user, "2026-11-01T10:00:00Z", "2026-11-01T11:00:00Z", Status.FREE);
        slot(user, "2026-11-01T12:00:00Z", "2026-11-01T15:00:00Z", Status.BUSY);
        var summary = get(user, "2026-11-01T09:00:00Z", "2026-11-01T14:00:00Z");
        assertThat(summary.from()).isEqualTo(Instant.parse("2026-11-01T09:00:00Z"));
        assertThat(summary.to()).isEqualTo(Instant.parse("2026-11-01T14:00:00Z"));
        assertThat(summary.intervals()).containsExactly(
                interval("2026-11-01T09:00:00Z", "2026-11-01T11:00:00Z", Availability.State.FREE),
                interval("2026-11-01T11:00:00Z", "2026-11-01T12:00:00Z", Availability.State.UNDECLARED),
                interval("2026-11-01T12:00:00Z", "2026-11-01T14:00:00Z", Availability.State.BUSY));
    }

    @Test
    void returnsUndeclaredForEmptyWindowAndGapsAtBothEnds() {
        var user = createUser();
        assertThat(get(user, "2026-11-01T09:00:00Z", "2026-11-01T12:00:00Z").intervals())
                .containsExactly(interval("2026-11-01T09:00:00Z", "2026-11-01T12:00:00Z", Availability.State.UNDECLARED));
        slot(user, "2026-11-01T10:00:00Z", "2026-11-01T11:00:00Z", Status.FREE);
        assertThat(get(user, "2026-11-01T09:00:00Z", "2026-11-01T12:00:00Z").intervals()).containsExactly(
                interval("2026-11-01T09:00:00Z", "2026-11-01T10:00:00Z", Availability.State.UNDECLARED),
                interval("2026-11-01T10:00:00Z", "2026-11-01T11:00:00Z", Availability.State.FREE),
                interval("2026-11-01T11:00:00Z", "2026-11-01T12:00:00Z", Availability.State.UNDECLARED));
    }

    @Test
    void rejectsInvalidWindowsAndUnknownUsers() {
        var user = createUser();
        assertThatThrownBy(() -> get(user, "2026-11-01T10:00:00Z", "2026-11-01T09:00:00Z"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> get(user, "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> get(UUID.randomUUID(), "2026-11-01T09:00:00Z", "2026-11-01T12:00:00Z"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
