package com.faithlog.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class PostgresFlywayMigrationTest {

	@Test
	@EnabledIfEnvironmentVariable(named = "FAITHLOG_RUN_POSTGRES_FLYWAY_TEST", matches = "true")
	void flywayMigratesCleanPostgresDatabase() throws Exception {
		String jdbcUrl = envOrDefault("FLYWAY_TEST_JDBC_URL", "jdbc:postgresql://localhost:5432/faithlog_test");
		String username = envOrDefault("FLYWAY_TEST_USERNAME", "faithlog");
		String password = envOrDefault("FLYWAY_TEST_PASSWORD", "faithlog");

		Flyway flyway = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.cleanDisabled(false)
			.locations("classpath:db/migration")
			.load();

		flyway.clean();
		MigrateResult result = flyway.migrate();

		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(2);
		assertThat(flyway.info().current()).isNotNull();
		assertThat(flyway.info().current().getVersion()).isGreaterThanOrEqualTo(MigrationVersion.fromVersion("6"));
		assertThat(flyway.info().current().getVersion()).isGreaterThanOrEqualTo(MigrationVersion.fromVersion("7"));
		assertTableExists(jdbcUrl, username, password, "users");
		assertTableExists(jdbcUrl, username, password, "poll_response_options");
		assertTableExists(jdbcUrl, username, password, "flyway_schema_history");
		assertColumnExists(jdbcUrl, username, password, "poll_templates", "allow_user_option_add");
		assertColumnExists(jdbcUrl, username, password, "polls", "allow_user_option_add");
		assertColumnExists(jdbcUrl, username, password, "poll_options", "user_added");
		assertColumnExists(jdbcUrl, username, password, "poll_options", "created_by_user_id");
		assertColumnExists(jdbcUrl, username, password, "users", "deleted_at");
		assertConstraintExists(jdbcUrl, username, password, "poll_options", "fk_poll_options_created_by_user");
		assertIndexExists(jdbcUrl, username, password, "user_fcm_tokens", "uk_user_fcm_tokens_active_token");
		assertIndexExists(jdbcUrl, username, password, "user_fcm_tokens", "uk_user_fcm_tokens_active_user_client");
		assertIndexExists(
			jdbcUrl, username, password,
			"charge_items", "idx_charge_items_campus_category_source"
		);
		assertIndexExists(
			jdbcUrl, username, password,
			"charge_items", "idx_charge_items_campus_category_status_user"
		);
		assertIndexExists(jdbcUrl, username, password, "campus_members", "idx_campus_members_user_id_id");
		assertIndexExists(jdbcUrl, username, password, "users", "uk_users_email_lower");
		assertCaseInsensitiveDuplicateEmailRejected(jdbcUrl, username, password);
		assertConstraintExists(jdbcUrl, username, password, "charge_items", "ck_charge_items_amount_positive");
		assertConstraintValidated(jdbcUrl, username, password, "charge_items", "ck_charge_items_amount_positive");
		assertInvalidChargeAmountRejected(jdbcUrl, username, password, 0);
		assertInvalidChargeAmountRejected(jdbcUrl, username, password, -1);
		assertAnnouncementNotificationTypeBoundary(jdbcUrl, username, password);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "FAITHLOG_RUN_POSTGRES_FLYWAY_TEST", matches = "true")
	void v14UpgradeAcceptsAnnouncementNotificationAndRejectsUnknownType() throws Exception {
		String jdbcUrl = envOrDefault("FLYWAY_TEST_JDBC_URL", "jdbc:postgresql://localhost:5432/faithlog_test");
		String username = envOrDefault("FLYWAY_TEST_USERNAME", "faithlog");
		String password = envOrDefault("FLYWAY_TEST_PASSWORD", "faithlog");
		Flyway beforeV14 = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.cleanDisabled(false)
			.locations("classpath:db/migration")
			.target("13")
			.load();

		beforeV14.clean();
		assertThat(beforeV14.migrate().success).isTrue();
		Flyway v14 = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.locations("classpath:db/migration")
			.load();

		assertThat(v14.migrate().success).isTrue();
		assertAnnouncementNotificationTypeBoundary(jdbcUrl, username, password);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "FAITHLOG_RUN_POSTGRES_FLYWAY_TEST", matches = "true")
	void v13FailsClosedWithoutChangingLegacyDuplicateEmails() throws Exception {
		String jdbcUrl = envOrDefault("FLYWAY_TEST_JDBC_URL", "jdbc:postgresql://localhost:5432/faithlog_test");
		String username = envOrDefault("FLYWAY_TEST_USERNAME", "faithlog");
		String password = envOrDefault("FLYWAY_TEST_PASSWORD", "faithlog");
		Flyway beforeV13 = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.cleanDisabled(false)
			.locations("classpath:db/migration")
			.target("12")
			.load();

		beforeV13.clean();
		assertThat(beforeV13.migrate().success).isTrue();
		insertLegacyUsersWithDuplicateCanonicalEmail(jdbcUrl, username, password);

		Flyway v13 = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.locations("classpath:db/migration")
			.load();

		assertThatThrownBy(v13::migrate)
			.isInstanceOf(FlywayException.class)
			.hasMessageContaining("V13__enforce_case_insensitive_user_email.sql")
			.hasMessageContaining("case-insensitive user email uniqueness");
		assertFlywayVersionMissing(jdbcUrl, username, password, "13");
		assertLegacyDuplicateEmailsPreserved(jdbcUrl, username, password);
	}

	private static void assertCaseInsensitiveDuplicateEmailRejected(
		String jdbcUrl, String username, String password
	) throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			PreparedStatement first = connection.prepareStatement(
				"insert into users (name, email, password_hash, role, is_active, token_version, created_at, updated_at) "
					+ "values ('first', ?, 'hash', 'USER', true, 0, now(), now())"
			);
			PreparedStatement duplicate = connection.prepareStatement(
				"insert into users (name, email, password_hash, role, is_active, token_version, created_at, updated_at) "
					+ "values ('second', ?, 'hash', 'USER', true, 0, now(), now())"
			)
		) {
			String suffix = java.util.UUID.randomUUID() + "@example.com";
			first.setString(1, "Case-" + suffix);
			first.executeUpdate();
			duplicate.setString(1, "case-" + suffix);
			assertThatThrownBy(duplicate::executeUpdate)
				.isInstanceOf(java.sql.SQLException.class)
				.hasMessageContaining("uk_users_email_lower");
		}
	}

	private static void insertLegacyUsersWithDuplicateCanonicalEmail(
		String jdbcUrl, String username, String password
	) throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			PreparedStatement statement = connection.prepareStatement(
				"insert into users (name, email, password_hash, role, is_active, token_version, created_at, updated_at) "
					+ "values ('legacy-a', 'Legacy@Example.com', 'hash', 'USER', true, 0, now(), now()), "
					+ "('legacy-b', 'legacy@example.com', 'hash', 'USER', true, 0, now(), now())"
			)
		) {
			statement.executeUpdate();
		}
	}

	private static void assertLegacyDuplicateEmailsPreserved(
		String jdbcUrl, String username, String password
	) throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			PreparedStatement statement = connection.prepareStatement(
				"select count(*) from users where lower(email) = 'legacy@example.com'"
			);
			ResultSet result = statement.executeQuery()
		) {
			assertThat(result.next()).isTrue();
			assertThat(result.getLong(1)).isEqualTo(2L);
		}
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "FAITHLOG_RUN_POSTGRES_FLYWAY_TEST", matches = "true")
	void v7FailsClosedWhenLegacyInvalidRowsExist() throws Exception {
		String jdbcUrl = envOrDefault("FLYWAY_TEST_JDBC_URL", "jdbc:postgresql://localhost:5432/faithlog_test");
		String username = envOrDefault("FLYWAY_TEST_USERNAME", "faithlog");
		String password = envOrDefault("FLYWAY_TEST_PASSWORD", "faithlog");
		Flyway flyway = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.cleanDisabled(false)
			.locations("classpath:db/migration")
			.target("6")
			.load();

		flyway.clean();
		assertThat(flyway.migrate().success).isTrue();
		insertLegacyInvalidCharge(jdbcUrl, username, password);

		Flyway v7 = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.locations("classpath:db/migration")
			.load();

		assertThatThrownBy(v7::migrate)
			.isInstanceOf(FlywayException.class)
			.hasMessageContaining("V7__enforce_positive_charge_amount.sql")
			.hasMessageContaining("ck_charge_items_amount_positive");
		assertFlywayVersionMissing(jdbcUrl, username, password, "7");
		assertLegacyInvalidChargePreserved(jdbcUrl, username, password);
	}

	private static void assertConstraintValidated(
		String jdbcUrl, String username, String password, String tableName, String constraintName
	) throws Exception {
		assertExists(
			jdbcUrl, username, password,
			"select exists (select 1 from pg_constraint c join pg_class t on t.oid = c.conrelid "
				+ "where t.relname = ? and c.conname = ? and c.convalidated)",
			tableName, constraintName
		);
	}

	private static void insertLegacyInvalidCharge(String jdbcUrl, String username, String password) throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			Statement session = connection.createStatement()
		) {
			session.execute("set session_replication_role = replica");
			try (PreparedStatement statement = connection.prepareStatement(
				"insert into charge_items (campus_id, user_id, payment_category, payment_account_id, "
					+ "bank_name_snapshot, account_number_snapshot, account_holder_snapshot, source_type, source_id, "
					+ "title, amount, status, created_at, updated_at) values "
					+ "(1, 1, 'PENALTY', 1, 'bank', 'account', 'holder', 'DEVOTION_RECORD', 8999, "
					+ "'legacy-invalid', 0, 'UNPAID', now(), now())"
			)) {
				statement.executeUpdate();
			} finally {
				session.execute("set session_replication_role = origin");
			}
		}
	}

	private static void assertFlywayVersionMissing(String jdbcUrl, String username, String password, String version)
		throws Exception {
		assertThat(exists(
			jdbcUrl,
			username,
			password,
			"select exists (select 1 from flyway_schema_history where version = ?)",
			version
		)).isFalse();
	}

	private static void assertLegacyInvalidChargePreserved(String jdbcUrl, String username, String password)
		throws Exception {
		assertThat(exists(
			jdbcUrl,
			username,
			password,
			"select exists (select 1 from charge_items where title = ? and amount = 0 and status = 'UNPAID')",
			"legacy-invalid"
		)).isTrue();
	}

	private static void assertInvalidChargeAmountRejected(
		String jdbcUrl, String username, String password, int amount
	) throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			Statement session = connection.createStatement()
		) {
			session.execute("set session_replication_role = replica");
			try (PreparedStatement statement = connection.prepareStatement(
				"insert into charge_items (campus_id, user_id, payment_category, payment_account_id, "
					+ "bank_name_snapshot, account_number_snapshot, account_holder_snapshot, source_type, source_id, "
					+ "title, amount, status, created_at, updated_at) values "
					+ "(1, 1, 'PENALTY', 1, 'bank', 'account', 'holder', 'DEVOTION_RECORD', ?, "
					+ "'invalid', ?, 'UNPAID', now(), now())"
			)) {
				statement.setLong(1, 9_000L + Math.abs(amount));
				statement.setInt(2, amount);
				assertThatThrownBy(statement::executeUpdate)
					.isInstanceOf(java.sql.SQLException.class)
					.hasMessageContaining("ck_charge_items_amount_positive");
			} finally {
				session.execute("set session_replication_role = origin");
			}
		}
	}

	private static void assertAnnouncementNotificationTypeBoundary(
		String jdbcUrl, String username, String password
	) throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			Statement statement = connection.createStatement()
		) {
			long suffix = Math.abs(java.util.UUID.randomUUID().getMostSignificantBits());
			String email = "migration-" + suffix + "@example.com";
			statement.executeUpdate("insert into users (name, email, password_hash, role, is_active, token_version, "
				+ "created_at, updated_at) values ('migration-user', '" + email
				+ "', 'hash', 'USER', true, 0, now(), now())");
			long userId;
			try (ResultSet result = statement.executeQuery("select id from users where email = '" + email + "'")) {
				assertThat(result.next()).isTrue();
				userId = result.getLong(1);
			}
			statement.executeUpdate("insert into notification_logs (user_id, notification_type, title, body, "
				+ "send_status, created_at) values (" + userId
				+ ", 'ANNOUNCEMENT_PUBLISHED', 'title', 'body', 'PENDING', now())");
			assertThatThrownBy(() -> statement.executeUpdate(
				"insert into notification_logs (user_id, notification_type, title, body, send_status, created_at) "
					+ "values (" + userId + ", 'UNAPPROVED_TYPE', 'title', 'body', 'PENDING', now())"))
				.isInstanceOf(java.sql.SQLException.class)
				.hasMessageContaining("ck_notification_logs_type");
		}
	}

	private static String envOrDefault(String name, String defaultValue) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private static void assertTableExists(String jdbcUrl, String username, String password, String tableName)
		throws Exception {
		assertExists(
			jdbcUrl,
			username,
			password,
			"select exists (select 1 from information_schema.tables "
				+ "where table_schema = 'public' and table_name = ?)",
			tableName
		);
	}

	private static void assertColumnExists(String jdbcUrl, String username, String password, String tableName,
		String columnName) throws Exception {
		assertExists(
			jdbcUrl,
			username,
			password,
			"select exists (select 1 from information_schema.columns "
				+ "where table_schema = 'public' and table_name = ? and column_name = ?)",
			tableName,
			columnName
		);
	}

	private static void assertConstraintExists(String jdbcUrl, String username, String password, String tableName,
		String constraintName) throws Exception {
		assertExists(
			jdbcUrl,
			username,
			password,
			"select exists (select 1 from information_schema.table_constraints "
				+ "where table_schema = 'public' and table_name = ? and constraint_name = ?)",
			tableName,
			constraintName
		);
	}

	private static void assertIndexExists(String jdbcUrl, String username, String password, String tableName,
		String indexName) throws Exception {
		assertExists(
			jdbcUrl,
			username,
			password,
			"select exists (select 1 from pg_indexes "
				+ "where schemaname = 'public' and tablename = ? and indexname = ?)",
			tableName,
			indexName
		);
	}

	private static void assertExists(String jdbcUrl, String username, String password, String sql, String... params)
		throws Exception {
		assertThat(exists(jdbcUrl, username, password, sql, params)).isTrue();
	}

	private static boolean exists(String jdbcUrl, String username, String password, String sql, String... params)
		throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			PreparedStatement statement = connection.prepareStatement(sql)
		) {
			for (int i = 0; i < params.length; i++) {
				statement.setString(i + 1, params[i]);
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getBoolean(1);
			}
		}
	}
}
