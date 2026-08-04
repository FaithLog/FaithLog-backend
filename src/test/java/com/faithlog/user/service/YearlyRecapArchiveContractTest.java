package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class YearlyRecapArchiveContractTest {

	private static final Path V17 = Path.of(
		"src/main/resources/db/migration/V17__add_yearly_recap_compact_archive.sql");
	private static final Path RETENTION = Path.of(
		"src/main/java/com/faithlog/batch/service/DataRetentionCleanupService.java");
	private static final Path ARCHIVE_PORT = Path.of(
		"src/main/java/com/faithlog/batch/service/port/YearlyRecapArchivePort.java");
	private static final Path ARCHIVE_ADAPTER = Path.of(
		"src/main/java/com/faithlog/user/infrastructure/repository/YearlyRecapArchiveAdapter.java");
	private static final Path SNAPSHOT_SERVICE = Path.of(
		"src/main/java/com/faithlog/user/service/YearlyRecapSnapshotService.java");

	@Test
	void compact_archive_preserves_only_minimum_recap_facts_and_coverage_watermark() throws Exception {
		String migration = Files.readString(V17);

		assertThat(migration)
			.contains("yearly_recap_archive_facts", "yearly_recap_archive_coverage",
				"COMMENT", "PRAYER", "DEVOTION_DAILY", "DEVOTION_WEEKLY", "PENALTY",
				"complete_from_year")
			.doesNotContain("comment_content", "prayer_content", "poll_response", "option_id",
				"memo", "account_number", "bank_name", "account_holder");
	}

	@Test
	void retention_archives_each_source_before_deleting_it_in_the_same_transaction() throws Exception {
		String retention = Files.readString(RETENTION);
		String port = Files.readString(ARCHIVE_PORT);

		assertThat(retention.indexOf("archiveExpiredPolls"))
			.isLessThan(retention.indexOf("pollCommentRepository.deleteByPollIdIn"));
		assertThat(retention.indexOf("archivePrayerSubmissionsBefore"))
			.isLessThan(retention.indexOf("prayerSubmissionRepository.deleteByCreatedAtBefore"));
		assertThat(retention.indexOf("archiveAnnualRecapFacts"))
			.isLessThan(retention.indexOf("dailyCheckRepository.deleteByRecordDateBetween"));
		assertThat(port).contains("archiveExpiredPolls", "archivePrayerSubmissionsBefore", "archiveAnnualRecapFacts");
	}

	@Test
	void user_owned_adapter_uses_consumer_port_and_snapshot_refuses_incomplete_coverage() throws Exception {
		String adapter = Files.readString(ARCHIVE_ADAPTER);
		String snapshotService = Files.readString(SNAPSHOT_SERVICE);

		assertThat(adapter)
			.contains("implements YearlyRecapArchivePort")
			.doesNotContain("PollCommentRepository", "PrayerSubmissionRepository",
				"DevotionDailyCheckRepository", "WeeklyDevotionRecordRepository", "ChargeItemRepository");
		assertThat(snapshotService).contains("isCoverageComplete", "emptyWithoutSnapshot");
	}
}
