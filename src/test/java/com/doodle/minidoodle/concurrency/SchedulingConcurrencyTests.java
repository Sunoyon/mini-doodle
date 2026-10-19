package com.doodle.minidoodle.concurrency;

import com.doodle.minidoodle.config.ApiException;
import com.doodle.minidoodle.dtos.CreateMeeting;
import com.doodle.minidoodle.dtos.CreateUser;
import com.doodle.minidoodle.dtos.MeetingResponse;
import com.doodle.minidoodle.dtos.SlotResponse;
import com.doodle.minidoodle.meeting.MeetingRepository;
import com.doodle.minidoodle.meeting.MeetingService;
import com.doodle.minidoodle.models.Status;
import com.doodle.minidoodle.slot.SlotRepository;
import com.doodle.minidoodle.slot.SlotService;
import com.doodle.minidoodle.user.UserService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(SchedulingConcurrencyTests.Checkpoints.class)
@Testcontainers
@Timeout(45)
class SchedulingConcurrencyTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    UserService users;
    @Autowired
    SlotService slots;
    @Autowired
    MeetingService meetings;
    @Autowired
    JdbcClient db;

    @FunctionalInterface
    interface Hook {
        void run() throws Exception;
    }

    private static volatile Hook beforeLock = () -> {
    };
    private static volatile Hook afterLock = () -> {
    };
    private static volatile Hook afterLocation = () -> {
    };

    @AfterEach
    void resetCheckpoints() {
        beforeLock = () -> {
        };
        afterLock = () -> {
        };
        afterLocation = () -> {
        };
    }

    // Test-only wrappers introduce checkpoints around real repository calls.
    // All persistence, transactions and row locking still execute against PostgreSQL.
    @TestConfiguration
    static class Checkpoints {
        @Bean
        static BeanPostProcessor repositoryCheckpoints() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof SlotRepository) && !(bean instanceof MeetingRepository)) return bean;
                    var proxy = new ProxyFactory(bean);
                    proxy.addAdvice((MethodInterceptor) invocation -> {
                        var method = invocation.getMethod().getName();
                        if (method.equals("lock")) beforeLock.run();
                        var result = invocation.proceed();
                        if (method.equals("lock")) afterLock.run();
                        if (method.equals("findLocation")) afterLocation.run();
                        return result;
                    });
                    return proxy.getProxy();
                }
            };
        }
    }

    private UUID user() {
        return users.create(new CreateUser("Race test", UUID.randomUUID() + "@example.com")).getId();
    }

    private SlotService.CreateSlot input(String start, String end) {
        return new SlotService.CreateSlot(OffsetDateTime.parse(start), OffsetDateTime.parse(end), Status.FREE);
    }

    private SlotResponse slot(UUID user) {
        return slots.create(user, input("2026-11-01T10:00:00Z", "2026-11-01T11:00:00Z"));
    }

    private MeetingResponse book(UUID user, UUID slot) {
        return meetings.book(user, slot, new CreateMeeting("Race test", null, List.of("guest@example.com")));
    }

    private long count(String sql, UUID id) {
        return db.sql(sql).param("id", id).query(Long.class).single();
    }

    private Status status(UUID slot) {
        return Status.valueOf(db.sql("select status from slot where id = :id").param("id", slot).query(String.class).single());
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(10, TimeUnit.SECONDS)).as("coordinated worker reached checkpoint").isTrue();
    }

    private static int outcome(Runnable operation, int success) {
        try {
            operation.run();
            return success;
        } catch (ApiException ex) {
            return ex.status().value();
        } catch (DataIntegrityViolationException ex) {
            return 409;
        }
    }

    @Test
    void simultaneousOverlappingInsertsHaveOneWinner() throws Exception {
        var user = user();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> first = () -> {
                ready.countDown();
                await(start);
                return outcome(() -> slots.create(user, input("2026-11-01T10:00:00Z", "2026-11-01T11:00:00Z")), 201);
            };
            Callable<Integer> second = () -> {
                ready.countDown();
                await(start);
                return outcome(() -> slots.create(user, input("2026-11-01T10:30:00Z", "2026-11-01T11:30:00Z")), 201);
            };
            var a = executor.submit(first);
            var b = executor.submit(second);
            try {
                await(ready);
            } finally {
                start.countDown();
            }
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(count("select count(*) from slot where calendar_id = :id", users.calendar(user).getId())).isEqualTo(1);
        assertThat(count("select count(*) from meeting m join slot s on s.id = m.slot_id where s.calendar_id = :id", users.calendar(user).getId())).isZero();
    }

    // Pause the winner while it holds the real PostgreSQL row lock. The other
    // transaction reaches the repository before the winner may commit.
    private List<Integer> orderedRace(Runnable first, int firstSuccess, Runnable second, int secondSuccess) throws Exception {
        var locked = new CountDownLatch(1);
        var contender = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        beforeLock = () -> {
            if (Thread.currentThread().getName().equals("contender")) contender.countDown();
        };
        afterLock = () -> {
            if (Thread.currentThread().getName().equals("winner")) {
                locked.countDown();
                await(release);
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                Thread.currentThread().setName("winner");
                return outcome(first, firstSuccess);
            });
            Future<Integer> b;
            try {
                await(locked);
                b = executor.submit(() -> {
                    Thread.currentThread().setName("contender");
                    return outcome(second, secondSuccess);
                });
                await(contender);
            } finally {
                release.countDown();
            }
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest(name = "booking versus deletion, bookingFirst={0}")
    @ValueSource(booleans = {true, false})
    void bookingRacingDeletionPreservesConsistency(boolean bookingFirst) throws Exception {
        var user = user();
        var slot = slot(user);
        Runnable booking = () -> book(user, slot.id());
        Runnable deletion = () -> slots.delete(user, slot.id());
        var result = bookingFirst ? orderedRace(booking, 201, deletion, 204) : orderedRace(deletion, 204, booking, 201);
        assertThat(result).containsExactlyElementsOf(bookingFirst ? List.of(201, 409) : List.of(204, 404));
        assertThat(count("select count(*) from slot where id = :id", slot.id())).isEqualTo(bookingFirst ? 1 : 0);
        assertThat(count("select count(*) from meeting where slot_id = :id", slot.id())).isEqualTo(bookingFirst ? 1 : 0);
        if (bookingFirst) assertThat(status(slot.id())).isEqualTo(Status.BUSY);
    }

    @ParameterizedTest(name = "booking versus update, bookingFirst={0}")
    @ValueSource(booleans = {true, false})
    void bookingRacingUpdatePreservesConsistency(boolean bookingFirst) throws Exception {
        var user = user();
        var slot = slot(user);
        Runnable booking = () -> book(user, slot.id());
        Runnable update = () -> slots.update(user, slot.id(), new SlotService.UpdateSlot(OffsetDateTime.parse("2026-11-01T10:15:00Z"), null, Status.BUSY));
        var result = bookingFirst ? orderedRace(booking, 201, update, 200) : orderedRace(update, 200, booking, 201);
        assertThat(result).containsExactlyElementsOf(bookingFirst ? List.of(201, 409) : List.of(200, 409));
        assertThat(status(slot.id())).isEqualTo(Status.BUSY);
        assertThat(count("select count(*) from meeting where slot_id = :id", slot.id())).isEqualTo(bookingFirst ? 1 : 0);
        var start = db.sql("select starts_at from slot where id = :id").param("id", slot.id()).query(OffsetDateTime.class).single().toInstant();
        assertThat(start).isEqualTo(bookingFirst ? slot.start() : OffsetDateTime.parse("2026-11-01T10:15:00Z").toInstant());
    }

    @Test
    void staleCancellationCannotFreeRebookedSlot() throws Exception {
        var user = user();
        var slot = slot(user);
        var old = book(user, slot.id());
        var located = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        afterLocation = () -> {
            if (Thread.currentThread().getName().equals("stale-cancellation")) {
                located.countDown();
                await(resume);
            }
        };
        try (var executor = Executors.newSingleThreadExecutor()) {
            var stale = executor.submit(() -> {
                Thread.currentThread().setName("stale-cancellation");
                return outcome(() -> meetings.cancel(old.id()), 204);
            });
            MeetingResponse replacement;
            try {
                await(located);
                meetings.cancel(old.id());
                replacement = book(user, slot.id());
            } finally {
                resume.countDown();
            }
            assertThat(stale.get(20, TimeUnit.SECONDS)).isEqualTo(404);
            assertThat(meetings.get(replacement.id())).isEqualTo(replacement);
            assertThat(count("select count(*) from meeting_participant where meeting_id = :id", old.id())).isZero();
            assertThat(count("select count(*) from meeting_participant where meeting_id = :id", replacement.id())).isEqualTo(1);
        }
        assertThat(count("select count(*) from meeting where slot_id = :id", slot.id())).isEqualTo(1);
        assertThat(status(slot.id())).isEqualTo(Status.BUSY);
    }
}
