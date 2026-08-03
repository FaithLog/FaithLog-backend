package com.faithlog.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.user.service.YearlyRecapSnapshotService;
import com.faithlog.user.service.policy.YearlyRecapPeriod;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
		assertTableExists(jdbcUrl, username, password, "yearly_recap_snapshots");
		assertTableExists(jdbcUrl, username, password, "yearly_recap_campuses");
		assertYearlyRecapSecurityAndIntegrity(jdbcUrl, username, password);
		assertSixYearlyRecapQueriesShareOnePostgresSnapshot(jdbcUrl, username, password);
		assertThat(flyway.info().current().getVersion()).isEqualTo(MigrationVersion.fromVersion("16"));
		assertCaseInsensitiveDuplicateEmailRejected(jdbcUrl, username, password);
		assertConstraintExists(jdbcUrl, username, password, "charge_items", "ck_charge_items_amount_positive");
		assertConstraintValidated(jdbcUrl, username, password, "charge_items", "ck_charge_items_amount_positive");
		assertInvalidChargeAmountRejected(jdbcUrl, username, password, 0);
		assertInvalidChargeAmountRejected(jdbcUrl, username, password, -1);
		assertAnnouncementNotificationTypeBoundary(jdbcUrl, username, password);
		assertCrossCampusAnnouncementImageRejected(jdbcUrl, username, password);
		assertPollNotificationTypeBoundary(jdbcUrl, username, password);
		assertCrossCampusPollImageRejected(jdbcUrl, username, password);
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
		assertCrossCampusAnnouncementImageRejected(jdbcUrl, username, password);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "FAITHLOG_RUN_POSTGRES_FLYWAY_TEST", matches = "true")
	void v16UpgradeAddsPollNoticeImagesAndOpenNotificationTypes() throws Exception {
		String jdbcUrl = envOrDefault("FLYWAY_TEST_JDBC_URL", "jdbc:postgresql://localhost:5432/faithlog_test");
		String username = envOrDefault("FLYWAY_TEST_USERNAME", "faithlog");
		String password = envOrDefault("FLYWAY_TEST_PASSWORD", "faithlog");
		Flyway beforeV16 = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.cleanDisabled(false)
			.locations("classpath:db/migration")
			.target("15")
			.load();

		beforeV16.clean();
		assertThat(beforeV16.migrate().success).isTrue();
		Flyway v16 = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.locations("classpath:db/migration")
			.load();

		assertThat(v16.migrate().success).isTrue();
		assertColumnExists(jdbcUrl, username, password, "polls", "notice");
		assertPollNotificationTypeBoundary(jdbcUrl, username, password);
		assertCrossCampusPollImageRejected(jdbcUrl, username, password);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "FAITHLOG_RUN_POSTGRES_FLYWAY_TEST", matches = "true")
	void v15UpgradesIssue237V14WithoutChangingItsChecksum() throws Exception {
		String jdbcUrl = envOrDefault("FLYWAY_TEST_JDBC_URL", "jdbc:postgresql://localhost:5432/faithlog_test");
		String username = envOrDefault("FLYWAY_TEST_USERNAME", "faithlog");
		String password = envOrDefault("FLYWAY_TEST_PASSWORD", "faithlog");
		Flyway issue237 = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.cleanDisabled(false)
			.locations("classpath:db/migration")
			.target("14")
			.load();

		issue237.clean();
		assertThat(issue237.migrate().success).isTrue();
		assertThat(issue237.info().current()).isNotNull();
		assertThat(issue237.info().current().getVersion()).isEqualTo(MigrationVersion.fromVersion("14"));
		Integer issue237Checksum = migrationChecksum(jdbcUrl, username, password, "14");
		assertFlywayVersionMissing(jdbcUrl, username, password, "15");

		Flyway issue236 = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.locations("classpath:db/migration")
			.load();
		MigrateResult result = issue236.migrate();

		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isEqualTo(1);
		assertThat(issue236.info().current()).isNotNull();
		assertThat(issue236.info().current().getVersion()).isEqualTo(MigrationVersion.fromVersion("15"));
		assertThat(migrationChecksum(jdbcUrl, username, password, "14")).isEqualTo(issue237Checksum);
		assertThat(migrationChecksum(jdbcUrl, username, password, "15")).isNotNull();
		assertTableExists(jdbcUrl, username, password, "yearly_recap_snapshots");
		assertTableExists(jdbcUrl, username, password, "yearly_recap_campuses");
		assertYearlyRecapSecurityAndIntegrity(jdbcUrl, username, password);
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

	private static Integer migrationChecksum(
		String jdbcUrl, String username, String password, String version
	) throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			PreparedStatement statement = connection.prepareStatement(
				"select checksum from flyway_schema_history where version = ? and success"
			)
		) {
			statement.setString(1, version);
			try (ResultSet result = statement.executeQuery()) {
				assertThat(result.next()).isTrue();
				return result.getObject(1, Integer.class);
			}
		}
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
		try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
			String suffix = java.util.UUID.randomUUID().toString();
			long userId = insertUser(connection, "migration-user", "migration-" + suffix + "@example.com");
			long campusId = insertCampus(connection, "migration-campus", "migration-" + suffix);
			java.util.UUID requestId = java.util.UUID.randomUUID();

			insertNotificationLog(connection, requestId, userId, campusId, "ANNOUNCEMENT_PUBLISHED");
			assertThatThrownBy(() -> insertNotificationLog(
				connection, java.util.UUID.randomUUID(), userId, campusId, "UNAPPROVED_TYPE"))
				.isInstanceOfSatisfying(java.sql.SQLException.class, exception -> {
					assertThat(exception.getSQLState()).isEqualTo("23514");
					assertThat(exception.getMessage()).contains("ck_notification_logs_type");
				});
		}
	}

	private static void assertCrossCampusAnnouncementImageRejected(
		String jdbcUrl, String username, String password
	) throws Exception {
		try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
			String suffix = java.util.UUID.randomUUID().toString();
			long userId = insertUser(connection, "tenant-user", "tenant-" + suffix + "@example.com");
			long announcementCampusId = insertCampus(
				connection, "announcement-campus", "announcement-" + suffix);
			long mediaCampusId = insertCampus(connection, "media-campus", "media-" + suffix);
			long categoryId = insertCategory(connection, announcementCampusId, "tenant-category");
			long announcementId = insertAnnouncement(connection, announcementCampusId, categoryId, userId);
			long mediaAssetId = insertReadyMediaAsset(connection, mediaCampusId, userId, suffix);

			assertThatThrownBy(() -> insertAnnouncementImage(
				connection, announcementCampusId, announcementId, mediaAssetId))
				.isInstanceOfSatisfying(java.sql.SQLException.class, exception -> {
					assertThat(exception.getSQLState()).isEqualTo("23503");
					assertThat(exception.getMessage()).contains("fk_announcement_images_media_asset");
				});
		}
	}

	private static void assertPollNotificationTypeBoundary(
		String jdbcUrl, String username, String password
	) throws Exception {
		try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
			String suffix = java.util.UUID.randomUUID().toString();
			long userId = insertUser(connection, "poll-notification-user", "poll-notification-" + suffix + "@example.com");
			long campusId = insertCampus(connection, "poll-notification-campus", "poll-notification-" + suffix);

			insertNotificationLog(connection, java.util.UUID.randomUUID(), userId, campusId, "MEAL_POLL_OPEN");
			insertNotificationLog(connection, java.util.UUID.randomUUID(), userId, campusId, "CUSTOM_POLL_OPEN");
		}
	}

	private static void assertCrossCampusPollImageRejected(
		String jdbcUrl, String username, String password
	) throws Exception {
		try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
			String suffix = java.util.UUID.randomUUID().toString();
			long userId = insertUser(connection, "poll-tenant-user", "poll-tenant-" + suffix + "@example.com");
			long pollCampusId = insertCampus(connection, "poll-campus", "poll-" + suffix);
			long mediaCampusId = insertCampus(connection, "poll-media-campus", "poll-media-" + suffix);
			long pollId = insertPoll(connection, pollCampusId, userId);
			long mediaAssetId = insertReadyMediaAsset(connection, mediaCampusId, userId, suffix);

			assertThatThrownBy(() -> insertPollImage(connection, pollCampusId, pollId, mediaAssetId))
				.isInstanceOfSatisfying(java.sql.SQLException.class, exception -> {
					assertThat(exception.getSQLState()).isEqualTo("23503");
					assertThat(exception.getMessage()).contains("fk_poll_images_media_asset");
				});
		}
	}

	private static long insertUser(Connection connection, String name, String email) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"insert into users (name, email, password_hash, role, is_active, token_version, created_at, updated_at) "
				+ "values (?, ?, 'hash', 'USER', true, 0, now(), now()) returning id"
		)) {
			statement.setString(1, name);
			statement.setString(2, email);
			return returnedId(statement);
		}
	}

	private static long insertCampus(Connection connection, String name, String inviteCode) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"insert into campuses (name, invite_code, is_active, created_at, updated_at) "
				+ "values (?, ?, true, now(), now()) returning id"
		)) {
			statement.setString(1, name);
			statement.setString(2, inviteCode);
			return returnedId(statement);
		}
	}

	private static void insertNotificationLog(
		Connection connection, java.util.UUID requestId, long userId, long campusId, String notificationType
	) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"insert into notification_logs (request_id, user_id, campus_id, notification_type, title, body, "
				+ "send_status, created_at) values (?, ?, ?, ?, 'title', 'body', 'PENDING', now())"
		)) {
			statement.setObject(1, requestId);
			statement.setLong(2, userId);
			statement.setLong(3, campusId);
			statement.setString(4, notificationType);
			statement.executeUpdate();
		}
	}

	private static long insertCategory(Connection connection, long campusId, String name) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"insert into announcement_categories (campus_id, name, color, display_order, is_active) "
				+ "values (?, ?, '#3B82F6', 0, true) returning id"
		)) {
			statement.setLong(1, campusId);
			statement.setString(2, name);
			return returnedId(statement);
		}
	}

	private static long insertAnnouncement(
		Connection connection, long campusId, long categoryId, long userId
	) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"insert into announcements (campus_id, category_id, author_id, title, content, is_pinned, status, "
				+ "publish_at, published_at) values (?, ?, ?, 'tenant-title', 'tenant-content', false, "
				+ "'PUBLISHED', now(), now()) returning id"
		)) {
			statement.setLong(1, campusId);
			statement.setLong(2, categoryId);
			statement.setLong(3, userId);
			return returnedId(statement);
		}
	}

	private static long insertPoll(Connection connection, long campusId, long userId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"insert into polls (campus_id, title, poll_type, selection_type, is_anonymous, allow_user_option_add, "
				+ "charge_generation_type, starts_at, ends_at, status, created_by, created_at, updated_at) "
				+ "values (?, 'tenant-poll', 'CUSTOM', 'SINGLE', false, false, 'NONE', now(), now() + interval '1 hour', "
				+ "'OPEN', ?, now(), now()) returning id"
		)) {
			statement.setLong(1, campusId);
			statement.setLong(2, userId);
			return returnedId(statement);
		}
	}

	private static void insertPollImage(
		Connection connection, long campusId, long pollId, long mediaAssetId
	) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"insert into poll_images (campus_id, poll_id, media_asset_id, display_order) values (?, ?, ?, 0)"
		)) {
			statement.setLong(1, campusId);
			statement.setLong(2, pollId);
			statement.setLong(3, mediaAssetId);
			statement.executeUpdate();
		}
	}

	private static long insertReadyMediaAsset(
		Connection connection, long campusId, long userId, String suffix
	) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"insert into media_assets (campus_id, owner_user_id, input_content_type, input_byte_size, "
				+ "expected_sha256, thumbnail_object_key, detail_object_key, output_sha256, width, height, "
				+ "output_byte_size, status, expires_at) values (?, ?, 'image/jpeg', 10, ?, ?, ?, ?, "
				+ "100, 100, 20, 'READY', now() + interval '1 day') returning id"
		)) {
			statement.setLong(1, campusId);
			statement.setLong(2, userId);
			statement.setString(3, "a".repeat(64));
			statement.setString(4, "tenant/" + suffix + "/thumb");
			statement.setString(5, "tenant/" + suffix + "/detail");
			statement.setString(6, "b".repeat(64));
			return returnedId(statement);
		}
	}

	private static void insertAnnouncementImage(
		Connection connection, long campusId, long announcementId, long mediaAssetId
	) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"insert into announcement_images (campus_id, announcement_id, media_asset_id, display_order) "
				+ "values (?, ?, ?, 0)"
		)) {
			statement.setLong(1, campusId);
			statement.setLong(2, announcementId);
			statement.setLong(3, mediaAssetId);
			statement.executeUpdate();
		}
	}

	private static long returnedId(PreparedStatement statement) throws Exception {
		try (ResultSet result = statement.executeQuery()) {
			assertThat(result.next()).isTrue();
			return result.getLong(1);
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

	private static void assertYearlyRecapSecurityAndIntegrity(
		String jdbcUrl, String username, String password
	) throws Exception {
		assertRowLevelSecurityEnabled(jdbcUrl, username, password, "yearly_recap_snapshots");
		assertRowLevelSecurityEnabled(jdbcUrl, username, password, "yearly_recap_campuses");
		assertConstraintExists(
			jdbcUrl, username, password, "yearly_recap_snapshots", "fk_yearly_recap_snapshots_user"
		);
		assertConstraintExists(
			jdbcUrl, username, password, "yearly_recap_campuses", "fk_yearly_recap_campuses_snapshot"
		);
		assertYearlyRecapOrphansRejected(jdbcUrl, username, password);
		assertThat(queryCount(
			jdbcUrl,
			username,
			password,
			"select count(*) from pg_indexes where schemaname = 'public' "
				+ "and tablename = 'yearly_recap_campuses' "
				+ "and indexdef like '%(yearly_recap_snapshot_id, campus_id)%'"
		)).isEqualTo(1L);
	}

	private static void assertYearlyRecapOrphansRejected(
		String jdbcUrl, String username, String password
	) throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			PreparedStatement orphanSnapshot = connection.prepareStatement(
				"insert into yearly_recap_snapshots (user_id, recap_year, has_recap_data, "
					+ "devotion_quiet_time_count, devotion_bible_reading_count, devotion_prayer_count, "
					+ "devotion_all_completed_day_count, devotion_submitted_week_count, "
					+ "devotion_longest_streak_days, prayer_submitted_week_count, "
					+ "prayer_participated_season_count, comment_written_count, "
					+ "penalty_total_count, penalty_total_amount, penalty_paid_count, penalty_paid_amount, "
					+ "penalty_unpaid_count, penalty_unpaid_amount, created_at, updated_at) "
					+ "values (9223372036854770000, 2025, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, "
					+ "0, 0, 0, 0, 0, 0, now(), now())"
			);
			PreparedStatement orphanCampus = connection.prepareStatement(
				"insert into yearly_recap_campuses (yearly_recap_snapshot_id, campus_id, "
					+ "campus_name, joined_date, joined_during_recap_year) "
					+ "values (9223372036854770000, 1, 'orphan', date '2025-01-01', false)"
			)
		) {
			assertThatThrownBy(orphanSnapshot::executeUpdate)
				.isInstanceOf(java.sql.SQLException.class)
				.hasFieldOrPropertyWithValue("SQLState", "23503")
				.hasMessageContaining("fk_yearly_recap_snapshots_user");
			assertThatThrownBy(orphanCampus::executeUpdate)
				.isInstanceOf(java.sql.SQLException.class)
				.hasFieldOrPropertyWithValue("SQLState", "23503")
				.hasMessageContaining("fk_yearly_recap_campuses_snapshot");
		}
	}

	private static void assertSixYearlyRecapQueriesShareOnePostgresSnapshot(
		String jdbcUrl, String username, String password
	) throws Exception {
		Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
			YearlyRecapSnapshotService.class.getDeclaredMethod(
				"getOrCreate", Long.class, YearlyRecapPeriod.class
			),
			Transactional.class
		);
		assertThat(transactional).isNotNull();

		try (
			Connection reader = DriverManager.getConnection(jdbcUrl, username, password);
			Connection writer = DriverManager.getConnection(jdbcUrl, username, password)
		) {
			reader.setAutoCommit(false);
			if (transactional.isolation() != Isolation.DEFAULT) {
				reader.setTransactionIsolation(transactional.isolation().value());
			}
			List<Long> observedCounts = new ArrayList<>();
			observedCounts.add(queryUserCount(reader));
			insertSnapshotIsolationUser(writer);
			for (int query = 1; query < 6; query++) {
				observedCounts.add(queryUserCount(reader));
			}
			reader.rollback();

			assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
			assertThat(reader.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
			assertThat(observedCounts).containsOnly(observedCounts.getFirst());
		}
	}

	private static long queryUserCount(Connection connection) throws Exception {
		try (
			PreparedStatement statement = connection.prepareStatement("select count(*) from users");
			ResultSet resultSet = statement.executeQuery()
		) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private static void insertSnapshotIsolationUser(Connection connection) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
			"insert into users (name, email, password_hash, role, is_active, token_version, created_at, updated_at) "
				+ "values ('snapshot isolation', ?, 'hash', 'USER', true, 0, now(), now())"
		)) {
			statement.setString(1, "snapshot-isolation-" + UUID.randomUUID() + "@example.com");
			statement.executeUpdate();
		}
	}

	private static void assertRowLevelSecurityEnabled(
		String jdbcUrl, String username, String password, String tableName
	) throws Exception {
		assertExists(
			jdbcUrl,
			username,
			password,
			"select coalesce((select c.relrowsecurity from pg_class c "
				+ "join pg_namespace n on n.oid = c.relnamespace "
				+ "where n.nspname = 'public' and c.relname = ?), false)",
			tableName
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

	private static long queryCount(String jdbcUrl, String username, String password, String sql) throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			PreparedStatement statement = connection.prepareStatement(sql);
			ResultSet resultSet = statement.executeQuery()
		) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}
}
