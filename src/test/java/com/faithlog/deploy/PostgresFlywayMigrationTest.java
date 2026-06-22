package com.faithlog.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
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
		assertThat(result.migrationsExecuted).isEqualTo(1);
		assertTableExists(jdbcUrl, username, password, "users");
		assertTableExists(jdbcUrl, username, password, "poll_response_options");
		assertTableExists(jdbcUrl, username, password, "flyway_schema_history");
	}

	private static String envOrDefault(String name, String defaultValue) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private static void assertTableExists(String jdbcUrl, String username, String password, String tableName)
		throws Exception {
		try (
			Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery(
				"select exists (select 1 from information_schema.tables "
					+ "where table_schema = 'public' and table_name = '" + tableName + "')"
			)
		) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getBoolean(1)).isTrue();
		}
	}
}
