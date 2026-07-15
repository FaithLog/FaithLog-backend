package com.faithlog.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlywayMigrationContractTest {

	private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V1__initial_schema.sql");
	private static final Path POSITIVE_CHARGE_MIGRATION = Path.of(
		"src/main/resources/db/migration/V7__enforce_positive_charge_amount.sql"
	);
	private static final Path MEAL_SETTLEMENT_MIGRATION = Path.of(
		"src/main/resources/db/migration/V8__add_meal_poll_settlement.sql"
	);
	private static final Path MULTIPLE_COFFEE_DUTY_MIGRATION = Path.of(
		"src/main/resources/db/migration/V9__allow_multiple_active_coffee_duties.sql"
	);
	private static final Path COFFEE_TEMPLATE_ACCOUNT_MIGRATION = Path.of(
		"src/main/resources/db/migration/V10__neutralize_coffee_template_accounts.sql"
	);
	private static final Path SUPABASE_DATA_API_SECURITY_MIGRATION = Path.of(
		"src/main/resources/db/migration/V11__secure_supabase_data_api.sql"
	);
	private static final Path CLOUD_RUN_DOC = Path.of("docs/deploy/cloud-run-supabase.md");
	private static final Path DOCKER_COMPOSE = Path.of("docker-compose.yml");
	private static final Path APPLICATION_DOCKER = Path.of("src/main/resources/application-docker.yml");
	private static final Path ENV_LOCAL_EXAMPLE = Path.of(".env.local.example");
	private static final Path ENV_DOCKER_EXAMPLE = Path.of(".env.docker.example");
	private static final Path ENV_PROD_EXAMPLE = Path.of(".env.prod.example");

	@Test
	void v1MigrationDefinesCurrentEntityTablesAndApprovedCodeColumns() throws IOException {
		String sql = Files.readString(MIGRATION);

		assertThat(sql).contains(
			"CREATE TABLE users",
			"CREATE TABLE campuses",
			"CREATE TABLE campus_members",
			"CREATE TABLE campus_duty_assignments",
			"CREATE TABLE penalty_rules",
			"CREATE TABLE payment_accounts",
			"CREATE TABLE weekly_devotion_records",
			"CREATE TABLE devotion_daily_checks",
			"CREATE TABLE coffee_brands",
			"CREATE TABLE coffee_menu_catalog",
			"CREATE TABLE poll_templates",
			"CREATE TABLE poll_template_options",
			"CREATE TABLE polls",
			"CREATE TABLE poll_options",
			"CREATE TABLE poll_responses",
			"CREATE TABLE poll_response_options",
			"CREATE TABLE poll_comments",
			"CREATE TABLE charge_items",
			"CREATE TABLE prayer_seasons",
			"CREATE TABLE prayer_groups",
			"CREATE TABLE prayer_group_members",
			"CREATE TABLE prayer_weeks",
			"CREATE TABLE prayer_submissions",
			"CREATE TABLE user_fcm_tokens",
			"CREATE TABLE notification_logs",
			"token_version BIGINT",
			"is_default BOOLEAN",
			"category VARCHAR(60)"
		);
	}

	@Test
	void v1MigrationAppliesNotionErdReferencesAsForeignKeys() throws IOException {
		String sql = Files.readString(MIGRATION);

		List<String> expectedForeignKeys = List.of(
			"fk_campus_members_campus",
			"fk_campus_members_user",
			"fk_campus_duty_assignments_campus",
			"fk_campus_duty_assignments_user",
			"fk_penalty_rules_campus",
			"fk_payment_accounts_campus",
			"fk_payment_accounts_owner_user",
			"fk_weekly_devotion_records_campus",
			"fk_weekly_devotion_records_user",
			"fk_devotion_daily_checks_weekly_record",
			"fk_coffee_menu_catalog_brand",
			"fk_poll_templates_campus",
			"fk_poll_templates_payment_account",
			"fk_poll_template_options_template",
			"fk_polls_campus",
			"fk_polls_template",
			"fk_polls_payment_account",
			"fk_polls_created_by",
			"fk_poll_options_poll",
			"fk_poll_responses_poll",
			"fk_poll_responses_user",
			"fk_poll_response_options_response",
			"fk_poll_response_options_option",
			"fk_poll_comments_poll",
			"fk_poll_comments_user",
			"fk_charge_items_campus",
			"fk_charge_items_user",
			"fk_charge_items_payment_account",
			"fk_prayer_seasons_campus",
			"fk_prayer_seasons_created_by",
			"fk_prayer_groups_season",
			"fk_prayer_group_members_group",
			"fk_prayer_group_members_user",
			"fk_prayer_weeks_campus",
			"fk_prayer_weeks_season",
			"fk_prayer_submissions_prayer_week",
			"fk_prayer_submissions_group",
			"fk_prayer_submissions_user",
			"fk_prayer_submissions_submitted_by",
			"fk_user_fcm_tokens_user",
			"fk_notification_logs_user",
			"fk_notification_logs_campus"
		);

		assertThat(sql).contains(expectedForeignKeys.toArray(String[]::new));
		assertThat(sql).doesNotContain("fk_charge_items_source_id");
	}

	@Test
	void cloudRunDeploymentDocumentDefinesSupabaseAndSecretSafeEnvironmentContract() throws IOException {
		String doc = Files.readString(CLOUD_RUN_DOC);

		assertThat(doc).contains(
			"Cloud Run",
			"SPRING_PROFILES_ACTIVE=prod",
			"SPRING_DATASOURCE_URL",
			"SPRING_DATASOURCE_USERNAME",
			"SPRING_DATASOURCE_PASSWORD",
			"JWT_SECRET",
			"FIREBASE_CONFIG_JSON",
			"SPRING_DATA_REDIS_HOST",
			"SPRING_DATA_REDIS_PORT",
			"SPRING_DATA_REDIS_PASSWORD",
			"SPRING_DATA_REDIS_SSL_ENABLED",
			"/api/v1/health",
			"Supabase",
			"Upstash",
			"Artifact Registry"
		);
		assertThat(doc).doesNotContain("Nginx", "Certbot");
	}

	@Test
	void dockerComposeAppPassesDeploymentRelevantEnvironmentVariables() throws IOException {
		String compose = Files.readString(DOCKER_COMPOSE);

		assertThat(compose).contains(
			"PORT: ${PORT:-8080}",
			"SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: ${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:-5}",
			"SPRING_FLYWAY_ENABLED: ${SPRING_FLYWAY_ENABLED:-false}",
			"SPRINGDOC_API_DOCS_ENABLED: ${SPRINGDOC_API_DOCS_ENABLED:-true}",
			"SPRINGDOC_SWAGGER_UI_ENABLED: ${SPRINGDOC_SWAGGER_UI_ENABLED:-true}",
			"FAITHLOG_SCHEDULER_ENABLED: ${FAITHLOG_SCHEDULER_ENABLED:-true}"
		);
	}

	@Test
	void dockerProfileAndEnvExamplesSeparateDockerRedisFromUpstashRedis() throws IOException {
		assertThat(APPLICATION_DOCKER).exists();
		assertThat(ENV_LOCAL_EXAMPLE).exists();
		assertThat(ENV_DOCKER_EXAMPLE).exists();
		assertThat(ENV_PROD_EXAMPLE).exists();

		String dockerProfile = Files.readString(APPLICATION_DOCKER);
		String dockerEnv = Files.readString(ENV_DOCKER_EXAMPLE);
		String prodEnv = Files.readString(ENV_PROD_EXAMPLE);
		String compose = Files.readString(DOCKER_COMPOSE);

		assertThat(dockerProfile).contains(
			"jdbc:postgresql://postgres:5432/faithlog",
			"host: ${SPRING_DATA_REDIS_HOST:redis}",
			"port: ${SPRING_DATA_REDIS_PORT:6379}"
		);
		assertThat(dockerProfile).doesNotContain("upstash", "SPRING_DATA_REDIS_PASSWORD", "SPRING_DATA_REDIS_SSL_ENABLED");

		assertThat(dockerEnv).contains(
			"SPRING_PROFILES_ACTIVE=docker",
			"SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/faithlog",
			"SPRING_DATA_REDIS_HOST=redis",
			"SPRING_DATA_REDIS_PORT=6379"
		);
		assertThat(dockerEnv).doesNotContain("upstash", "SPRING_DATA_REDIS_PASSWORD");

		assertThat(prodEnv).contains(
			"SPRING_PROFILES_ACTIVE=prod",
			"SPRING_DATASOURCE_URL=jdbc:postgresql://<supabase-host>:5432/<database>?sslmode=require",
			"SPRING_DATA_REDIS_HOST=<upstash-redis-host>",
			"SPRING_DATA_REDIS_PORT=6379",
			"SPRING_DATA_REDIS_PASSWORD=<upstash-redis-password>",
			"SPRING_DATA_REDIS_SSL_ENABLED=true"
		);

		assertThat(compose).contains(
			"SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-docker}",
			"SPRING_DATA_REDIS_HOST: ${SPRING_DATA_REDIS_HOST:-redis}"
		);
		assertThat(compose).doesNotContain("upstash");
	}

	@Test
	void v7MigrationAddsPositiveChargeAmountConstraintWithoutEditingV1() throws IOException {
		assertThat(POSITIVE_CHARGE_MIGRATION).exists();
		String sql = Files.readString(POSITIVE_CHARGE_MIGRATION);
		String v1 = Files.readString(MIGRATION);

		assertThat(sql).contains(
			"ck_charge_items_amount_positive",
			"CHECK (amount > 0)",
			"NOT VALID",
			"VALIDATE CONSTRAINT"
		);
		assertThat(sql).doesNotContain("IF NOT EXISTS");
		assertThat(v1).doesNotContain("ck_charge_items_amount_positive");
	}

	@Test
	void v8MigrationAddsMealDutyAccountPollAndNormalizedSettlementWithoutEditingV1ToV7() throws IOException {
		assertThat(MEAL_SETTLEMENT_MIGRATION).exists();
		String sql = Files.readString(MEAL_SETTLEMENT_MIGRATION);

		assertThat(sql).contains(
			"'MEAL'",
			"meal_poll_settlements",
			"meal_poll_charge_groups",
			"requested_total_amount BIGINT",
			"actual_total_amount BIGINT",
			"rounding_adjustment BIGINT",
			"response_count_snapshot",
			"amount_per_member INTEGER",
			"UNIQUE (poll_id)",
			"UNIQUE (poll_id, option_id)",
			"uk_campus_duty_assignments_active_coffee",
			"uk_campus_duty_assignments_active_meal_user",
			"uk_payment_accounts_active_meal_owner"
		);
		assertThat(sql).doesNotContain("DELETE FROM", "UPDATE ");
		assertThat(Files.readString(MIGRATION)).doesNotContain("MEAL");
		assertThat(Files.readString(POSITIVE_CHARGE_MIGRATION)).doesNotContain("MEAL");
	}

	@Test
	void v9MigrationAllowsMultipleCoffeeDutiesAndKeepsPerUserActiveIdempotency() throws IOException {
		assertThat(MULTIPLE_COFFEE_DUTY_MIGRATION).exists();
		String sql = Files.readString(MULTIPLE_COFFEE_DUTY_MIGRATION);

		assertThat(sql).contains(
			"DROP INDEX uk_campus_duty_assignments_active_coffee",
			"CREATE UNIQUE INDEX uk_campus_duty_assignments_active_coffee_user",
			"(campus_id, duty_type, user_id)",
			"WHERE is_active = TRUE AND duty_type = 'COFFEE'"
		);
		assertThat(sql).doesNotContain("DELETE FROM", "UPDATE ");
	}

	@Test
	void v10MigrationMakesValidCoffeeTemplatesAccountNeutralAndQuarantinesLegacyMixedRows() throws IOException {
		assertThat(COFFEE_TEMPLATE_ACCOUNT_MIGRATION).exists();
		String sql = Files.readString(COFFEE_TEMPLATE_ACCOUNT_MIGRATION);

		assertThat(sql).contains(
			"UPDATE poll_templates",
			"SET payment_account_id = NULL",
			"WHERE poll_type = 'COFFEE'",
			"AND payment_account_id IS NOT NULL",
			"SET is_active = FALSE",
			"auto_create_enabled = FALSE",
			"poll_type = 'COFFEE'",
			"charge_generation_type = 'OPTION_PRICE'",
			"payment_category = 'COFFEE'",
			"AND NOT ("
		);
		assertThat(sql).doesNotContain("DELETE FROM", "DROP ", "ALTER TABLE");
	}

	@Test
	void v11MigrationBlocksSupabaseDataApiAccessWithoutChangingApplicationRows() throws IOException {
		assertThat(SUPABASE_DATA_API_SECURITY_MIGRATION).exists();
		String sql = Files.readString(SUPABASE_DATA_API_SECURITY_MIGRATION);

		assertThat(sql).contains(
			"ALTER TABLE %I ENABLE ROW LEVEL SECURITY",
			"n.nspname = 'public'",
			"c.relkind IN ('r', 'p')",
			"c.relname <> 'flyway_schema_history'",
			"REVOKE USAGE, CREATE ON SCHEMA public FROM PUBLIC",
			"REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM PUBLIC",
			"REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC",
			"REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC",
			"ARRAY['anon', 'authenticated', 'service_role']",
			"FROM pg_roles",
			"REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM %I",
			"REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM %I",
			"REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM %I",
			"ALTER DEFAULT PRIVILEGES IN SCHEMA public",
			"REVOKE ALL PRIVILEGES ON TABLES FROM %I",
			"REVOKE ALL PRIVILEGES ON SEQUENCES FROM %I",
			"REVOKE EXECUTE ON FUNCTIONS FROM %I"
		);
		assertThat(sql).doesNotContain(
			"CREATE POLICY",
			"FORCE ROW LEVEL SECURITY",
			"DELETE FROM",
			"INSERT INTO",
			"UPDATE "
		);
	}
}
