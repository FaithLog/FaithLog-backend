package com.faithlog.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
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
		assertThat(flyway.info().current().getVersion()).isGreaterThanOrEqualTo(MigrationVersion.fromVersion("2"));
		assertTableExists(jdbcUrl, username, password, "users");
		assertTableExists(jdbcUrl, username, password, "poll_response_options");
		assertTableExists(jdbcUrl, username, password, "flyway_schema_history");
		assertColumnExists(jdbcUrl, username, password, "poll_templates", "allow_user_option_add");
		assertColumnExists(jdbcUrl, username, password, "polls", "allow_user_option_add");
		assertColumnExists(jdbcUrl, username, password, "poll_options", "user_added");
		assertColumnExists(jdbcUrl, username, password, "poll_options", "created_by_user_id");
		assertConstraintExists(jdbcUrl, username, password, "poll_options", "fk_poll_options_created_by_user");
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

	private static void assertExists(String jdbcUrl, String username, String password, String sql, String... params)
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
				assertThat(resultSet.getBoolean(1)).isTrue();
			}
		}
	}
}
