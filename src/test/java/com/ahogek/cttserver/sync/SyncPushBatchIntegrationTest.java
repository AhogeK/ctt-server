package com.ahogek.cttserver.sync;

import com.ahogek.cttserver.auth.dto.LoginRequest;
import com.ahogek.cttserver.auth.dto.UserRegisterRequest;
import com.ahogek.cttserver.common.BaseIntegrationTest;
import com.ahogek.cttserver.sync.dto.SyncPushResponse;
import com.ahogek.cttserver.sync.dto.SyncSessionDto;
import com.ahogek.cttserver.sync.service.SyncPushService;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Verifies that the push path issues one multi-row INSERT per table instead of one single-row
 * INSERT per session.
 *
 * <p>A {@link BeanPostProcessor} wraps the auto-configured {@link DataSource} so every {@code
 * INSERT INTO coding_sessions} and {@code INSERT INTO session_changes} execution is counted. With
 * 500 new sessions the push must collapse all inserts into a handful of multi-row statements; the
 * old per-row path issued 500 statements per table.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-08-30
 */
@BaseIntegrationTest
@TestPropertySource(properties = {"ctt.mail.outbox.poll-interval-ms=999999999"})
@DisplayName("Sync Push Batch Integration Tests")
class SyncPushBatchIntegrationTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SyncPushService syncPushService;

    private static final String PASSWORD = "StrongPass123!";
    private static final String DISPLAY_NAME = "BatchTestUser";
    private static final int BATCH_SIZE = 500;
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @TestConfiguration(proxyBeanMethods = false)
    static class CountingDataSourceConfiguration {

        static final AtomicInteger sessionInserts = new AtomicInteger();
        static final AtomicInteger changeInserts = new AtomicInteger();
        static final java.util.concurrent.atomic.AtomicReference<String> sessionSql =
                new java.util.concurrent.atomic.AtomicReference<>();
        static final java.util.concurrent.atomic.AtomicReference<String> changeSql =
                new java.util.concurrent.atomic.AtomicReference<>();

        @Bean
        static BeanPostProcessor countingDataSourcePostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(
                        @NonNull Object bean, @NonNull String beanName) {
                    if (bean instanceof DataSource delegate
                            && !bean.getClass().getName().contains("Counting")) {
                        return countingProxy(delegate);
                    }
                    return bean;
                }

                private DataSource countingProxy(DataSource delegate) {
                    return (DataSource)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class[] {DataSource.class},
                                    (_, method, args) -> {
                                        if (method.getName().equals("getConnection")) {
                                            Connection connection =
                                                    (Connection) invoke(method, delegate, args);
                                            return connectionProxy(connection);
                                        }
                                        return invoke(method, delegate, args);
                                    });
                }

                private Connection connectionProxy(Connection delegate) {
                    return (Connection)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class[] {Connection.class},
                                    (_, cMethod, cArgs) -> {
                                        if (cMethod.getName().startsWith("prepare")
                                                && cArgs != null
                                                && cArgs[0] instanceof String sql) {
                                            PreparedStatement statement =
                                                    (PreparedStatement)
                                                            invoke(cMethod, delegate, cArgs);
                                            return statementProxy(statement, sql);
                                        }
                                        return invoke(cMethod, delegate, cArgs);
                                    });
                }

                private PreparedStatement statementProxy(PreparedStatement delegate, String sql) {
                    return (PreparedStatement)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class[] {PreparedStatement.class},
                                    (_, pMethod, pArgs) -> {
                                        if (pMethod.getName().equals("executeBatch")) {
                                            int[] counts = (int[]) invoke(pMethod, delegate, pArgs);
                                            for (int i = 0; i < counts.length; i++) {
                                                countInsert(sql);
                                            }
                                            return counts;
                                        } else if (pMethod.getName().equals("executeUpdate")
                                                || pMethod.getName().equals("execute")) {
                                            countInsert(sql);
                                        }
                                        return invoke(pMethod, delegate, pArgs);
                                    });
                }

                private Object invoke(Method method, Object target, Object[] args)
                        throws Throwable {
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }

                private void countInsert(String sql) {
                    String normalized = sql.toLowerCase();
                    if (normalized.contains("insert into coding_sessions")) {
                        sessionInserts.incrementAndGet();
                        sessionSql.set(sql);
                    } else if (normalized.contains("insert into session_changes")) {
                        changeInserts.incrementAndGet();
                        changeSql.set(sql);
                    }
                }
            };
        }
    }

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM session_changes").update();
        jdbcClient.sql("DELETE FROM sync_cursors").update();
        jdbcClient.sql("DELETE FROM coding_sessions").update();
        jdbcClient.sql("DELETE FROM devices").update();
        jdbcClient.sql("DELETE FROM api_keys").update();
        jdbcClient.sql("DELETE FROM mail_outbox").update();
        jdbcClient.sql("DELETE FROM email_verification_tokens").update();
        jdbcClient.sql("DELETE FROM refresh_tokens").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    private String uniqueEmail() {
        return "batch-test." + UUID.randomUUID() + "@test.example";
    }

    private void registerVerifyAndLogin(String email) throws Exception {
        UserRegisterRequest request =
                new UserRegisterRequest(email, DISPLAY_NAME, PASSWORD, "1.0.0", null);
        assertThat(
                        mvc.post()
                                .uri("/api/v1/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .hasStatus(200);

        String bodyHtml =
                jdbcClient
                        .sql(
                                "SELECT body_html FROM mail_outbox WHERE recipient = ? ORDER BY created_at DESC LIMIT 1")
                        .param(email)
                        .query(String.class)
                        .single();
        Matcher matcher = TOKEN_PATTERN.matcher(bodyHtml);
        assertThat(matcher.find())
                .as("Verification token not found in email body for %s", email)
                .isTrue();
        String verifyToken = matcher.group(1);
        assertThat(mvc.get().uri("/api/v1/auth/verify-email?token=" + verifyToken)).hasStatus(200);

        LoginRequest login = new LoginRequest(email, PASSWORD, "device-batch", null);
        String loginResponse =
                mvc.post()
                        .uri("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login))
                        .exchange()
                        .getResponse()
                        .getContentAsString();
        String accessToken =
                objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
    }

    private UUID userIdOf(String email) {
        return jdbcClient
                .sql("SELECT id FROM users WHERE email = ?")
                .param(email)
                .query(UUID.class)
                .single();
    }

    private UUID insertDevice(UUID userId) {
        UUID deviceId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO devices
                            (id, user_id, device_name, platform, ide_name, ide_version, app_version,
                             last_ip, created_at, last_seen_at, updated_at)
                        VALUES (?, ?, 'sync-device', 'macos', 'IntelliJ IDEA', '2026.1', '1.0.0',
                                '127.0.0.1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .param(deviceId)
                .param(userId)
                .update();
        return deviceId;
    }

    private static int countValuesGroups(String sql) {
        if (sql == null) {
            return 0;
        }
        int valuesIndex = sql.indexOf("VALUES");
        if (valuesIndex < 0) {
            return 0;
        }
        String tail = sql.substring(valuesIndex + "VALUES".length());
        int groups = 0;
        int pos = tail.indexOf("(?, ");
        while (pos != -1) {
            groups++;
            pos = tail.indexOf("(?, ", pos + 1);
        }
        return groups;
    }

    private SyncSessionDto dto(int index, Instant base) {
        return new SyncSessionDto(
                UUID.randomUUID(),
                "batch-project",
                "Java",
                base.plusSeconds(index * 60L),
                base.plusSeconds(index * 60L + 1200),
                base.plusSeconds(index * 60L),
                index + 1,
                false);
    }

    @Test
    @DisplayName("should batch INSERT statements when pushing a large batch")
    void shouldBatchInsert_whenPushingLargeBatch() throws Exception {
        String email = uniqueEmail();
        registerVerifyAndLogin(email);
        UUID userId = userIdOf(email);
        UUID deviceId = insertDevice(userId);

        CountingDataSourceConfiguration.sessionInserts.set(0);
        CountingDataSourceConfiguration.changeInserts.set(0);

        Instant base = Instant.parse("2026-08-30T00:00:00Z");
        List<SyncSessionDto> sessions =
                IntStream.range(0, BATCH_SIZE).mapToObj(i -> dto(i, base)).toList();

        SyncPushResponse response = syncPushService.push(userId, deviceId, sessions);

        int sessionInsertStatements = CountingDataSourceConfiguration.sessionInserts.get();
        int changeInsertStatements = CountingDataSourceConfiguration.changeInserts.get();

        // The push issues ONE multi-row INSERT per table (VALUES (...), (...), ...) covering
        // all 500 rows, instead of 500 single-row INSERTs. Prove it at the SQL-text level: the
        // executed statement must actually contain one VALUES group per row.
        assertThat(sessionInsertStatements)
                .as("coding_sessions INSERT should execute exactly once")
                .isEqualTo(1);
        assertThat(changeInsertStatements)
                .as("session_changes INSERT should execute exactly once")
                .isEqualTo(1);
        assertThat(countValuesGroups(CountingDataSourceConfiguration.sessionSql.get()))
                .as("coding_sessions multi-row INSERT should carry %d row groups", BATCH_SIZE)
                .isEqualTo(BATCH_SIZE);
        assertThat(countValuesGroups(CountingDataSourceConfiguration.changeSql.get()))
                .as("session_changes multi-row INSERT should carry %d row groups", BATCH_SIZE)
                .isEqualTo(BATCH_SIZE);

        assertThat(response.nextCursor()).isPositive();
        assertThat(
                        jdbcClient
                                .sql(
                                        "SELECT COUNT(*) FROM coding_sessions WHERE user_id = ? AND is_deleted = false")
                                .param(userId)
                                .query(Long.class)
                                .single())
                .isEqualTo(BATCH_SIZE);
        assertThat(
                        jdbcClient
                                .sql("SELECT COUNT(*) FROM session_changes WHERE user_id = ?")
                                .param(userId)
                                .query(Long.class)
                                .single())
                .isEqualTo(BATCH_SIZE);
    }
}
