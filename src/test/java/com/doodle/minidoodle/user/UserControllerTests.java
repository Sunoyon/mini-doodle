package com.doodle.minidoodle.user;

import java.util.UUID;

import com.doodle.minidoodle.dtos.CreateUser;
import com.doodle.minidoodle.dtos.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserControllerTests {

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

	@Test
	void createsUserAndCalendarAndRetrievesUser() {
		var client = RestClient.create("http://localhost:" + port);
		var email = UUID.randomUUID() + "@EXAMPLE.COM";
		var response = client.post().uri("/users")
				.body(new CreateUser(" Alice ", email))
				.retrieve().toEntity(UserResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		var user = response.getBody();
		assertThat(user).isNotNull();
		assertThat(user.name()).isEqualTo("Alice");
		assertThat(user.email()).isEqualTo(email.toLowerCase(java.util.Locale.ROOT));
		assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/users/" + user.id());
		assertThat(users.calendar(user.id()).getUserId()).isEqualTo(user.id());
		assertThat(client.get().uri("/users/{id}", user.id()).retrieve().body(UserResponse.class)).isEqualTo(user);

		assertThatThrownBy(() -> client.post().uri("/users")
				.body(new CreateUser("Alice", email)).retrieve().toBodilessEntity())
				.isInstanceOf(HttpClientErrorException.Conflict.class)
				.satisfies(error -> assertProblem(error, 409, "Email already registered"));
	}

	@Test
	void rejectsInvalidUserAndReturnsNotFoundForUnknownUser() {
		var client = RestClient.create("http://localhost:" + port);
		assertThatThrownBy(() -> client.post().uri("/users")
				.body(new CreateUser(" ", "invalid")).retrieve().toBodilessEntity())
				.isInstanceOf(HttpClientErrorException.BadRequest.class)
				.satisfies(error -> assertProblem(error, 400, null));
		assertThatThrownBy(() -> client.get().uri("/users/{id}", UUID.randomUUID())
				.retrieve().toBodilessEntity())
				.isInstanceOf(HttpClientErrorException.NotFound.class)
				.satisfies(error -> assertProblem(error, 404, "User not found"));
	}

	private static void assertProblem(Throwable error, int status, String detail) {
		var response = (HttpClientErrorException) error;
		assertThat(response.getResponseHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		var problem = response.getResponseBodyAs(ProblemDetail.class);
		assertThat(problem).isNotNull();
		assertThat(problem.getStatus()).isEqualTo(status);
		if (detail != null) {
			assertThat(problem.getDetail()).isEqualTo(detail);
		}
	}

}
