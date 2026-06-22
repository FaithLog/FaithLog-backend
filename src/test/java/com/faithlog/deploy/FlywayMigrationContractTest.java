package com.faithlog.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlywayMigrationContractTest {

	private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V1__initial_schema.sql");
	private static final Path CLOUD_RUN_DOC = Path.of("docs/deploy/cloud-run-supabase.md");
	private static final Path DOCKER_COMPOSE = Path.of("docker-compose.yml");

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
			"/api/v1/health",
			"Supabase",
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
}
