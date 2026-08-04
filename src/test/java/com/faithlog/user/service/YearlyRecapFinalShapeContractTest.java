package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class YearlyRecapFinalShapeContractTest {

	private static final Path RESPONSE = Path.of(
		"src/main/java/com/faithlog/user/controller/dto/response/YearlyRecapResponse.java");
	private static final Path SNAPSHOT = Path.of(
		"src/main/java/com/faithlog/user/domain/entity/YearlyRecapSnapshot.java");
	private static final Path AGGREGATE_ADAPTER = Path.of(
		"src/main/java/com/faithlog/user/infrastructure/repository/JpaYearlyRecapAggregateQueryAdapter.java");
	private static final Path V15 = Path.of(
		"src/main/resources/db/migration/V15__add_yearly_recap_snapshots.sql");

	@Test
	void yearly_recap_exposes_only_own_comment_and_devotion_penalty_summary_without_poll_participation() throws Exception {
		String response = Files.readString(RESPONSE);
		String snapshot = Files.readString(SNAPSHOT);
		String adapter = Files.readString(AGGREGATE_ADAPTER);
		String migration = Files.readString(V15);

		assertThat(response)
			.contains("CommentActivityResponse", "PenaltySummaryResponse")
			.doesNotContain("PollActivityResponse", "participatedCount", "wedServicePollCount",
				"saturdayLeaderPollCount", "coffeePollCount", "mealPollCount", "customPollCount");
		assertThat(snapshot)
			.contains("commentWrittenCount", "penaltyTotalCount", "penaltyTotalAmount",
				"penaltyPaidCount", "penaltyPaidAmount", "penaltyUnpaidCount", "penaltyUnpaidAmount")
			.doesNotContain("pollParticipatedCount", "pollWedServiceCount", "pollSaturdayLeaderCount",
				"pollCoffeeCount", "pollMealCount", "pollCustomCount");
		assertThat(adapter)
			.contains("comment.user_id = :userId", "comment.deleted_at is null",
				"charge.payment_category = 'PENALTY'", "charge.source_type = 'DEVOTION_RECORD'",
				"charge.status in ('PAID', 'UNPAID')")
			.doesNotContain("PollResponse", "group by poll.pollType");
		assertThat(migration)
			.contains("comment_written_count", "penalty_total_count", "penalty_total_amount",
				"penalty_paid_count", "penalty_paid_amount", "penalty_unpaid_count", "penalty_unpaid_amount")
			.doesNotContain("poll_participated_count", "poll_wed_service_count",
				"poll_saturday_leader_count", "poll_coffee_count", "poll_meal_count", "poll_custom_count");
	}
}
