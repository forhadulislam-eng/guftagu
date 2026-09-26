package com.guftagu.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class IdentitySchemaIT {
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    private static DataSource dataSource;

    @BeforeAll
    static void migrateEmptyDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
        dataSource = flyway.getConfiguration().getDataSource();
    }

    @Test
    void appliesAllMigrationsAndCreatesIdentityTables() throws SQLException {
        assertThat(tableExists("identity_users")).isTrue();
        assertThat(tableExists("identity_phone_numbers")).isTrue();
        assertThat(tableExists("identity_password_credentials")).isTrue();
        assertThat(tableExists("identity_sessions")).isTrue();
        assertThat(tableExists("identity_refresh_token_families")).isTrue();
        assertThat(tableExists("identity_refresh_tokens")).isTrue();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank")) {
            int expectedVersion = 1;
            while (result.next()) {
                assertThat(result.getString(1)).isEqualTo(Integer.toString(expectedVersion++));
            }
            assertThat(expectedVersion).isEqualTo(7);
        }
    }

    @Test
    void enforcesIdentityRelationshipsConstraintsAndTimestampTypes() throws SQLException {
        UUID userId = insertUser("ACTIVE");
        UUID phoneId = UUID.randomUUID();
        execute("INSERT INTO identity_phone_numbers (id, user_id, normalized_e164, verification_status, is_primary) VALUES (?, ?, ?, 'VERIFIED', true)", phoneId, userId, "+14155552671");

        assertThatSqlFails("INSERT INTO identity_phone_numbers (id, user_id, normalized_e164, verification_status, is_primary) VALUES (?, ?, ?, 'VERIFIED', false)", UUID.randomUUID(), userId, "+14155552671");
        assertThatSqlFails("INSERT INTO identity_phone_numbers (id, user_id, normalized_e164, verification_status, is_primary) VALUES (?, ?, ?, 'VERIFIED', true)", UUID.randomUUID(), userId, "+14155552672");
        assertThatSqlFails("INSERT INTO identity_phone_numbers (id, user_id, normalized_e164, verification_status, is_primary) VALUES (?, ?, ?, 'VERIFIED', false)", UUID.randomUUID(), userId, "14155552673");
        assertThatSqlFails("DELETE FROM identity_users WHERE id = ?", userId);

        execute("INSERT INTO identity_password_credentials (user_id, password_hash) VALUES (?, ?)", userId, "$argon2id$test");
        assertThatSqlFails("INSERT INTO identity_password_credentials (user_id, password_hash) VALUES (?, ?)", userId, "$argon2id$second");

        UUID sessionId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        execute("INSERT INTO identity_sessions (id, user_id, client_type, expires_at) VALUES (?, ?, 'WEB', ?)", sessionId, userId, expiresAt);
        UUID familyId = UUID.randomUUID();
        execute("INSERT INTO identity_refresh_token_families (id, session_id, expires_at) VALUES (?, ?, ?)", familyId, sessionId, expiresAt.plusDays(29));
        assertThatSqlFails("INSERT INTO identity_refresh_token_families (id, session_id, expires_at) VALUES (?, ?, ?)", UUID.randomUUID(), sessionId, expiresAt.plusDays(29));

        UUID parentTokenId = UUID.randomUUID();
        execute("INSERT INTO identity_refresh_tokens (id, family_id, secret_hash, expires_at, consumed_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)", parentTokenId, familyId, new byte[]{1}, expiresAt);
        UUID activeTokenId = UUID.randomUUID();
        execute("INSERT INTO identity_refresh_tokens (id, family_id, parent_token_id, secret_hash, expires_at) VALUES (?, ?, ?, ?, ?)", activeTokenId, familyId, parentTokenId, new byte[]{2}, expiresAt);
        assertThatSqlFails("INSERT INTO identity_refresh_tokens (id, family_id, secret_hash, expires_at) VALUES (?, ?, ?, ?)", UUID.randomUUID(), familyId, new byte[]{3}, expiresAt);
        assertThatSqlFails("INSERT INTO identity_refresh_tokens (id, family_id, parent_token_id, secret_hash, expires_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), familyId, UUID.randomUUID(), new byte[]{4}, expiresAt);
        assertThatSqlFails("DELETE FROM identity_sessions WHERE id = ?", sessionId);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT pg_typeof(created_at)::text FROM identity_users WHERE id = ?")) {
            statement.setObject(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("timestamp with time zone");
            }
        }
    }

    private static UUID insertUser(String status) throws SQLException {
        UUID userId = UUID.randomUUID();
        execute("INSERT INTO identity_users (id, account_status) VALUES (?, ?)", userId, status);
        return userId;
    }

    private static boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private static void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private static void assertThatSqlFails(String sql, Object... values) {
        assertThatThrownBy(() -> execute(sql, values)).isInstanceOf(SQLException.class);
    }
}
