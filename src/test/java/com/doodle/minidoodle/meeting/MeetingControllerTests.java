package com.doodle.minidoodle.meeting;

import com.doodle.minidoodle.dtos.*;
import com.doodle.minidoodle.models.Status;
import com.doodle.minidoodle.slot.SlotService;
import com.doodle.minidoodle.user.UserService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
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
class MeetingControllerTests {

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

    @Autowired
    JdbcClient db;

    private UUID user() {
        return users.create(new CreateUser("Alice", UUID.randomUUID() + "@example.com")).getId();
    }

    private SlotResponse slot(UUID user, Status status) {
        return slots.create(user, new SlotService.CreateSlot(OffsetDateTime.parse("2026-11-01T10:00:00Z"),
                OffsetDateTime.parse("2026-11-01T11:00:00Z"), status));
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private CreateMeeting request() {
        return new CreateMeeting(" Planning ", null, List.of("Z@example.com", "a@example.com", "A@EXAMPLE.COM"));
    }

    private MeetingResponse book(UUID user, UUID slot) {
        return client().post().uri("/users/{user}/slots/{slot}/meeting", user, slot)
                .body(request()).retrieve().body(MeetingResponse.class);
    }

    private Status status(UUID user) {
        return slots.find(user, Instant.parse("2026-11-01T09:00:00Z"),
                Instant.parse("2026-11-01T12:00:00Z"), null).getFirst().status();
    }

    @Test
    void booksRetrievesCancelsAndAllowsRebooking() {
        var user = user();
        var slot = slot(user, Status.FREE);
        var response = client().post().uri("/users/{user}/slots/{slot}/meeting", user, slot.id())
                .body(request()).retrieve().toEntity(MeetingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var meeting = response.getBody();
        assertThat(meeting).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/meetings/" + meeting.id());
        assertThat(meeting.slotId()).isEqualTo(slot.id());
        assertThat(meeting.title()).isEqualTo("Planning");
        assertThat(meeting.description()).isEmpty();
        assertThat(meeting.participants()).containsExactly("a@example.com", "z@example.com");
        assertThat(status(user)).isEqualTo(Status.BUSY);
        assertThat(client().get().uri("/meetings/{id}", meeting.id()).retrieve().body(MeetingResponse.class))
                .isEqualTo(meeting);
        assertThatThrownBy(() -> book(user, slot.id())).isInstanceOf(HttpClientErrorException.Conflict.class);
        assertThatThrownBy(() -> slots.update(user, slot.id(), new SlotService.UpdateSlot(null, null, Status.FREE)))
                .isInstanceOf(com.doodle.minidoodle.config.ApiException.class);
        assertThat(client().delete().uri("/meetings/{id}", meeting.id()).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(user)).isEqualTo(Status.FREE);
        assertThat(db.sql("select count(*) from meeting_participant where meeting_id = :id")
                .param("id", meeting.id()).query(Long.class).single()).isZero();
        assertThatThrownBy(() -> client().get().uri("/meetings/{id}", meeting.id()).retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> client().delete().uri("/meetings/{id}", meeting.id()).retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThat(book(user, slot.id()).id()).isNotEqualTo(meeting.id());
    }

    @Test
    void rejectsBusySlotsWrongOwnersAndInvalidRequests() {
        var user = user();
        var slot = slot(user, Status.BUSY);
        assertThatThrownBy(() -> book(user, slot.id())).isInstanceOf(HttpClientErrorException.Conflict.class);
        assertThatThrownBy(() -> book(user(), slot.id())).isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> client().post().uri("/users/{user}/slots/{slot}/meeting", user, slot.id())
                .body(new CreateMeeting(" ", null, List.of("invalid"))).retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> client().get().uri("/meetings/{id}", UUID.randomUUID()).retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void supportsMeetingWithoutParticipants() {
        var user = user();
        var slot = slot(user, Status.FREE);
        var meeting = client().post().uri("/users/{user}/slots/{slot}/meeting", user, slot.id())
                .body(new CreateMeeting("Solo", "Notes", List.of())).retrieve().body(MeetingResponse.class);
        assertThat(client().get().uri("/meetings/{id}", meeting.id()).retrieve().body(MeetingResponse.class))
                .isEqualTo(meeting);
        assertThat(meeting.participants()).isEmpty();
    }

    @Test
    void concurrentBookingsHaveOneWinner() throws Exception {
        var user = user();
        var slot = slot(user, Status.FREE);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt = () -> {
                start.await();
                try {
                    book(user, slot.id());
                    return 201;
                } catch (HttpClientErrorException ex) {
                    return ex.getStatusCode().value();
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.sql("select count(*) from meeting where slot_id = :id").param("id", slot.id())
                .query(Long.class).single()).isEqualTo(1);
        assertThat(status(user)).isEqualTo(Status.BUSY);
    }

    @Test
    void concurrentCancellationsHaveOneWinner() throws Exception {
        var user = user();
        var meeting = book(user, slot(user, Status.FREE).id());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt = () -> {
                start.await();
                try {
                    return client().delete().uri("/meetings/{id}", meeting.id())
                            .retrieve().toBodilessEntity().getStatusCode().value();
                } catch (HttpClientErrorException ex) {
                    return ex.getStatusCode().value();
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 404);
        }
        assertThat(status(user)).isEqualTo(Status.FREE);
    }
}
