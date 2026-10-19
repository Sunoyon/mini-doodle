package com.doodle.minidoodle.slot;

import com.doodle.minidoodle.dtos.CreateUser;
import com.doodle.minidoodle.dtos.SlotResponse;
import com.doodle.minidoodle.models.Status;
import com.doodle.minidoodle.user.UserService;
import java.util.UUID;
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
class SlotControllerTests {

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
    JdbcClient db;

    private UUID createUser() {
        return users.create(new CreateUser("Alice", UUID.randomUUID() + "@example.com")).getId();
    }

    private RestClient client(UUID userId) {
        return RestClient.create("http://localhost:" + port + "/users/" + userId + "/slots");
    }

    private SlotResponse create(RestClient client, String start, String end) {
        return client.post().body(new SlotService.CreateSlot(java.time.OffsetDateTime.parse(start),
                java.time.OffsetDateTime.parse(end), Status.FREE))
                .retrieve().body(SlotResponse.class);
    }

    private SlotResponse[] find(RestClient client, String from, String to, String status) {
        return client.get().uri(uri -> {
            var builder = uri.queryParam("from", from).queryParam("to", to);
            if (status != null) builder.queryParam("status", status);
            return builder.build();
        }).retrieve().body(SlotResponse[].class);
    }

    @Test
    void createsListsUpdatesAndDeletesSlots() {
        var client = client(createUser());
        var slot = create(client, "2026-11-01T10:00:00Z", "2026-11-01T11:00:00Z");
        assertThat(slot.id()).isNotNull();
        var updated = client.patch().uri("/{id}", slot.id())
                .body(new SlotService.UpdateSlot(null, null, Status.BUSY))
                .retrieve().body(SlotResponse.class);
        assertThat(updated.status()).isEqualTo(Status.BUSY);
        assertThat(updated.start()).isEqualTo(slot.start());
        assertThat(updated.end()).isEqualTo(slot.end());
        assertThat(find(client, "2026-11-01T09:00:00Z", "2026-11-01T12:00:00Z", "BUSY"))
                .containsExactly(updated);
        assertThat(find(client, "2026-11-01T09:00:00Z", "2026-11-01T12:00:00Z", "FREE")).isEmpty();
        var response = client.delete().uri("/{id}", slot.id()).retrieve().toBodilessEntity();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(find(client, "2026-11-01T09:00:00Z", "2026-11-01T12:00:00Z", null)).isEmpty();
    }

    @Test
    void enforcesHalfOpenIntervalsAndRollsBackOverlappingUpdates() {
        var client = client(createUser());
        var first = create(client, "2026-11-01T10:00:00Z", "2026-11-01T11:00:00Z");
        var second = create(client, "2026-11-01T11:00:00Z", "2026-11-01T12:00:00Z");
        assertThat(find(client, "2026-11-01T11:00:00Z", "2026-11-01T12:00:00Z", null))
                .containsExactly(second);
        assertThatThrownBy(() -> create(client, "2026-11-01T10:30:00Z", "2026-11-01T11:30:00Z"))
                .isInstanceOf(HttpClientErrorException.Conflict.class);
        assertThatThrownBy(() -> client.patch().uri("/{id}", second.id())
                .body(new SlotService.UpdateSlot(java.time.OffsetDateTime.parse("2026-11-01T10:30:00Z"), null, null))
                .retrieve().toBodilessEntity()).isInstanceOf(HttpClientErrorException.Conflict.class);
        assertThat(find(client, "2026-11-01T09:00:00Z", "2026-11-01T13:00:00Z", null))
                .containsExactly(first, second);
    }

    @Test
    void validatesIntervalsPrecisionWindowAndOwnership() {
        var client = client(createUser());
        assertThatThrownBy(() -> create(client, "2026-11-01T11:00:00Z", "2026-11-01T10:00:00Z"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> create(client, "2026-11-01T10:00:00.000000001Z", "2026-11-01T11:00:00Z"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> client.post().body("{}").header("Content-Type", "application/json")
                .retrieve().toBodilessEntity()).isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThatThrownBy(() -> find(client, "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z", null))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
        var slot = create(client, "2026-11-01T10:00:00Z", "2026-11-01T11:00:00Z");
        var other = client(createUser());
        assertThatThrownBy(() -> other.patch().uri("/{id}", slot.id())
                .body(new SlotService.UpdateSlot(null, null, Status.BUSY)).retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> other.delete().uri("/{id}", slot.id()).retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        assertThatThrownBy(() -> create(client(UUID.randomUUID()), "2026-11-01T10:00:00Z", "2026-11-01T11:00:00Z"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void refusesToEditOrDeleteBookedSlot() {
        var client = client(createUser());
        var slot = create(client, "2026-11-01T10:00:00Z", "2026-11-01T11:00:00Z");
        db.sql("insert into meeting (id, slot_id, title, description) values (:id, :slot, 'Meeting', '')")
                .param("id", UUID.randomUUID()).param("slot", slot.id()).update();
        assertThatThrownBy(() -> client.patch().uri("/{id}", slot.id())
                .body(new SlotService.UpdateSlot(null, null, Status.BUSY)).retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class);
        assertThatThrownBy(() -> client.delete().uri("/{id}", slot.id()).retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class);
    }
}
