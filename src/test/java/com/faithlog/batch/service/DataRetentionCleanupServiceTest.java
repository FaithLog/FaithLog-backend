package com.faithlog.batch.service;

import com.faithlog.batch.service.result.DataRetentionCleanupResult;
import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.infrastructure.repository.PaymentAccountRepository;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.notification.domain.entity.NotificationLog;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.entity.MealPollChargeGroup;
import com.faithlog.poll.domain.entity.MealPollSettlement;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollComment;
import com.faithlog.poll.domain.entity.PollOption;
import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.domain.entity.PollResponseOption;
import com.faithlog.poll.domain.type.MealChargeCalculationType;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.MealPollChargeGroupRepository;
import com.faithlog.poll.infrastructure.repository.MealPollSettlementRepository;
import com.faithlog.poll.infrastructure.repository.PollCommentRepository;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.prayer.domain.entity.PrayerGroup;
import com.faithlog.prayer.domain.entity.PrayerSeason;
import com.faithlog.prayer.domain.entity.PrayerSubmission;
import com.faithlog.prayer.domain.entity.PrayerWeek;
import com.faithlog.prayer.infrastructure.repository.PrayerGroupRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerSeasonRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerSubmissionRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerWeekRepository;
import com.faithlog.support.NotificationConcurrencyTestConfig.InMemoryNotificationConcurrencyPort;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataRetentionCleanupServiceTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant DAILY_NOW = ZonedDateTime.of(2027, 2, 2, 4, 30, 0, 0, SEOUL_ZONE).toInstant();
	private static final Instant ANNUAL_DUE = ZonedDateTime.of(2027, 2, 1, 4, 30, 0, 0, SEOUL_ZONE).toInstant();
	private static final Path MEAL_SETTLEMENT_MIGRATION = Path.of(
		"src/main/resources/db/migration/V8__add_meal_poll_settlement.sql"
	);

	@Autowired
	private DataRetentionCleanupService dataRetentionCleanupService;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private PollRepository pollRepository;

	@Autowired
	private PollOptionRepository pollOptionRepository;

	@Autowired
	private PollResponseRepository pollResponseRepository;

	@Autowired
	private PollResponseOptionRepository pollResponseOptionRepository;

	@Autowired
	private PollCommentRepository pollCommentRepository;

	@Autowired
	private MealPollSettlementRepository mealPollSettlementRepository;

	@Autowired
	private MealPollChargeGroupRepository mealPollChargeGroupRepository;

	@Autowired
	private PrayerSeasonRepository prayerSeasonRepository;

	@Autowired
	private PrayerGroupRepository prayerGroupRepository;

	@Autowired
	private PrayerWeekRepository prayerWeekRepository;

	@Autowired
	private PrayerSubmissionRepository prayerSubmissionRepository;

	@Autowired
	private WeeklyDevotionRecordRepository weeklyRecordRepository;

	@Autowired
	private DevotionDailyCheckRepository dailyCheckRepository;

	@Autowired
	private PaymentAccountRepository paymentAccountRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private InMemoryNotificationConcurrencyPort notificationConcurrencyPort;

	@AfterEach
	void resetLockPort() {
		notificationConcurrencyPort.reset();
	}

	@Test
	void cleanupDaily_deletes_notification_logs_older_than_fourteen_days_only() {
		User user = saveUser("retention-notification-user@example.com");
		Campus campus = saveCampus("retention-notification");
		NotificationLog expired = saveNotificationLog(user.id(), campus.id(), "2027-01-18T04:29:59Z");
		NotificationLog boundary = saveNotificationLog(user.id(), campus.id(), "2027-01-19T19:30:00Z");
		NotificationLog fresh = saveNotificationLog(user.id(), campus.id(), "2027-01-25T00:00:00Z");

		DataRetentionCleanupResult result = dataRetentionCleanupService.cleanupDaily(DAILY_NOW);

		assertThat(result.notificationLogsDeleted()).isEqualTo(1);
		assertThat(notificationLogRepository.findById(expired.id())).isEmpty();
		assertThat(notificationLogRepository.findById(boundary.id())).isPresent();
		assertThat(notificationLogRepository.findById(fresh.id())).isPresent();
	}

	@Test
	void cleanupDaily_deletes_expired_poll_graph_in_foreign_key_order_and_preserves_recent_poll() {
		User user = saveUser("retention-poll-user@example.com");
		Campus campus = saveCampus("retention-poll");
		PollGraph expired = savePollGraph(campus.id(), user.id(), DAILY_NOW.minusSeconds(31L * 24 * 60 * 60));
		PollGraph fresh = savePollGraph(campus.id(), user.id(), DAILY_NOW.minusSeconds(29L * 24 * 60 * 60));

		DataRetentionCleanupResult result = dataRetentionCleanupService.cleanupDaily(DAILY_NOW);

		assertThat(result.pollsDeleted()).isEqualTo(1);
		assertThat(pollResponseOptionRepository.findById(expired.responseOptionId())).isEmpty();
		assertThat(pollResponseRepository.findById(expired.responseId())).isEmpty();
		assertThat(pollCommentRepository.findById(expired.commentId())).isEmpty();
		assertThat(pollOptionRepository.findById(expired.optionId())).isEmpty();
		assertThat(pollRepository.findById(expired.pollId())).isEmpty();
		assertThat(pollRepository.findById(fresh.pollId())).isPresent();
		assertThat(pollOptionRepository.findById(fresh.optionId())).isPresent();
		assertThat(pollResponseRepository.findById(fresh.responseId())).isPresent();
		assertThat(pollResponseOptionRepository.findById(fresh.responseOptionId())).isPresent();
		assertThat(pollCommentRepository.findById(fresh.commentId())).isPresent();
	}

	@Test
	void cleanupDaily_deletes_expired_settled_meal_poll_graph_with_migration_cascades() throws IOException {
		installMealSettlementForeignKeysFromMigration();
		User user = saveUser("retention-settled-meal-user@example.com");
		Campus campus = saveCampus("retention-settled-meal");
		SettledMealPollGraph expired = saveSettledMealPollGraph(
			campus.id(), user.id(), DAILY_NOW.minusSeconds(31L * 24 * 60 * 60)
		);

		DataRetentionCleanupResult result = dataRetentionCleanupService.cleanupDaily(DAILY_NOW);

		assertThat(result.pollsDeleted()).isEqualTo(1);
		assertThat(mealPollChargeGroupRepository.findById(expired.chargeGroupId())).isEmpty();
		assertThat(mealPollSettlementRepository.findById(expired.settlementId())).isEmpty();
		assertThat(pollResponseOptionRepository.findById(expired.responseOptionId())).isEmpty();
		assertThat(pollResponseRepository.findById(expired.responseId())).isEmpty();
		assertThat(pollOptionRepository.findById(expired.optionId())).isEmpty();
		assertThat(pollRepository.findById(expired.pollId())).isEmpty();
	}

	@Test
	void cleanupDaily_deletes_only_soft_deleted_poll_comments_older_than_thirty_days() {
		User user = saveUser("retention-comment-user@example.com");
		Campus campus = saveCampus("retention-comment");
		Poll poll = savePoll(campus.id(), DAILY_NOW.plusSeconds(3600));
		PollComment expiredDeleted = saveComment(poll.id(), user.id(), true, "2027-01-01T00:00:00Z");
		PollComment freshDeleted = saveComment(poll.id(), user.id(), true, "2027-01-15T00:00:00Z");
		PollComment activeOld = saveComment(poll.id(), user.id(), false, "2027-01-01T00:00:00Z");

		DataRetentionCleanupResult result = dataRetentionCleanupService.cleanupDaily(DAILY_NOW);

		assertThat(result.softDeletedPollCommentsDeleted()).isEqualTo(1);
		assertThat(pollCommentRepository.findById(expiredDeleted.id())).isEmpty();
		assertThat(pollCommentRepository.findById(freshDeleted.id())).isPresent();
		assertThat(pollCommentRepository.findById(activeOld.id())).isPresent();
	}

	@Test
	void cleanupDaily_deletes_prayer_submissions_older_than_one_year_by_created_at() {
		User user = saveUser("retention-prayer-user@example.com");
		Campus campus = saveCampus("retention-prayer");
		PrayerSubmission expired = savePrayerSubmission(campus.id(), user.id(), LocalDate.of(2025, 12, 22));
		PrayerSubmission boundary = savePrayerSubmission(campus.id(), user.id(), LocalDate.of(2026, 2, 2));
		PrayerSubmission fresh = savePrayerSubmission(campus.id(), user.id(), LocalDate.of(2026, 6, 1));
		updateCreatedAt("prayer_submissions", expired.id(), "2026-02-01T19:29:59Z");
		updateCreatedAt("prayer_submissions", boundary.id(), "2026-02-01T19:30:00Z");
		updateCreatedAt("prayer_submissions", fresh.id(), "2026-06-01T00:00:00Z");

		DataRetentionCleanupResult result = dataRetentionCleanupService.cleanupDaily(DAILY_NOW);

		assertThat(result.prayerSubmissionsDeleted()).isEqualTo(1);
		assertThat(prayerSubmissionRepository.findById(expired.id())).isEmpty();
		assertThat(prayerSubmissionRepository.findById(boundary.id())).isPresent();
		assertThat(prayerSubmissionRepository.findById(fresh.id())).isPresent();
	}

	@Test
	void cleanupAnnualIfDue_deletes_previous_year_devotion_daily_and_weekly_records_only_on_february_first() {
		User user = saveUser("retention-devotion-user@example.com");
		Campus campus = saveCampus("retention-devotion");
		WeeklyDevotionRecord previousYearWeekly = saveWeeklyRecord(campus.id(), user.id(), LocalDate.of(2026, 6, 15));
		DevotionDailyCheck previousYearDaily = saveDailyCheck(previousYearWeekly.id(), LocalDate.of(2026, 6, 15));
		WeeklyDevotionRecord currentYearWeekly = saveWeeklyRecord(campus.id(), user.id(), LocalDate.of(2027, 1, 4));
		DevotionDailyCheck currentYearDaily = saveDailyCheck(currentYearWeekly.id(), LocalDate.of(2027, 1, 4));

		DataRetentionCleanupResult skipped = dataRetentionCleanupService.cleanupAnnualIfDue(DAILY_NOW);

		assertThat(skipped.totalDeleted()).isZero();
		assertThat(weeklyRecordRepository.findById(previousYearWeekly.id())).isPresent();
		assertThat(dailyCheckRepository.findById(previousYearDaily.id())).isPresent();

		DataRetentionCleanupResult result = dataRetentionCleanupService.cleanupAnnualIfDue(ANNUAL_DUE);

		assertThat(result.devotionDailyChecksDeleted()).isEqualTo(1);
		assertThat(result.weeklyDevotionRecordsDeleted()).isEqualTo(1);
		assertThat(dailyCheckRepository.findById(previousYearDaily.id())).isEmpty();
		assertThat(weeklyRecordRepository.findById(previousYearWeekly.id())).isEmpty();
		assertThat(dailyCheckRepository.findById(currentYearDaily.id())).isPresent();
		assertThat(weeklyRecordRepository.findById(currentYearWeekly.id())).isPresent();
	}

	@Test
	void cleanupAnnualIfDue_deletes_previous_year_terminal_charges_and_preserves_unpaid() {
		User user = saveUser("retention-charge-user@example.com");
		Campus campus = saveCampus("retention-charge");
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.id(),
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"테스트은행",
			"111-222",
			"회계",
			user.id()
		));
		ChargeItem paid = saveCharge(campus.id(), user.id(), account.id(), 1001L, ChargeStatus.PAID, "2026-06-01T00:00:00Z");
		ChargeItem waived = saveCharge(campus.id(), user.id(), account.id(), 1002L, ChargeStatus.WAIVED, "2026-06-01T00:00:00Z");
		ChargeItem canceled = saveCharge(campus.id(), user.id(), account.id(), 1003L, ChargeStatus.CANCELED, "2026-06-01T00:00:00Z");
		ChargeItem unpaid = saveCharge(campus.id(), user.id(), account.id(), 1004L, ChargeStatus.UNPAID, "2026-06-01T00:00:00Z");
		ChargeItem currentYearPaid = saveCharge(campus.id(), user.id(), account.id(), 1005L, ChargeStatus.PAID, "2027-01-01T00:00:00Z");

		DataRetentionCleanupResult result = dataRetentionCleanupService.cleanupAnnualIfDue(ANNUAL_DUE);

		assertThat(result.chargeItemsDeleted()).isEqualTo(3);
		assertThat(chargeItemRepository.findById(paid.id())).isEmpty();
		assertThat(chargeItemRepository.findById(waived.id())).isEmpty();
		assertThat(chargeItemRepository.findById(canceled.id())).isEmpty();
		assertThat(chargeItemRepository.findById(unpaid.id())).isPresent();
		assertThat(chargeItemRepository.findById(currentYearPaid.id())).isPresent();
	}

	@Test
	void cleanupDaily_skips_when_redis_lock_is_unavailable() {
		User user = saveUser("retention-lock-user@example.com");
		Campus campus = saveCampus("retention-lock");
		NotificationLog expired = saveNotificationLog(user.id(), campus.id(), "2027-01-01T00:00:00Z");
		notificationConcurrencyPort.fail();

		DataRetentionCleanupResult result = dataRetentionCleanupService.cleanupDaily(DAILY_NOW);

		assertThat(result.totalDeleted()).isZero();
		assertThat(notificationLogRepository.findById(expired.id())).isPresent();
	}

	private NotificationLog saveNotificationLog(Long userId, Long campusId, String createdAt) {
		NotificationLog log = notificationLogRepository.saveAndFlush(NotificationLog.skipped(
			UUID.randomUUID(),
			userId,
			campusId,
			NotificationType.CUSTOM,
			null,
			null,
			"테스트",
			"본문",
			"TEST"
		));
		updateCreatedAt("notification_logs", log.id(), createdAt);
		return log;
	}

	private PollGraph savePollGraph(Long campusId, Long userId, Instant endsAt) {
		Poll poll = savePoll(campusId, endsAt);
		PollOption option = pollOptionRepository.save(PollOption.create(poll.id(), "참석", null, 0, 1));
		PollResponse response = pollResponseRepository.save(PollResponse.create(poll.id(), userId, "memo"));
		PollResponseOption responseOption = pollResponseOptionRepository.save(PollResponseOption.create(response.id(), option.id()));
		PollComment comment = pollCommentRepository.save(PollComment.create(poll.id(), userId, "댓글"));
		return new PollGraph(poll.id(), option.id(), response.id(), responseOption.id(), comment.id());
	}

	private SettledMealPollGraph saveSettledMealPollGraph(Long campusId, Long userId, Instant endsAt) {
		Poll poll = Poll.createMeal(
			campusId,
			"정리 대상 정산 밥 투표",
			false,
			true,
			endsAt.minusSeconds(3600),
			endsAt,
			userId
		);
		poll.close();
		poll = pollRepository.saveAndFlush(poll);
		PollOption option = pollOptionRepository.saveAndFlush(PollOption.create(poll.id(), "한식", null, 0, 1));
		PollResponse response = pollResponseRepository.saveAndFlush(PollResponse.create(poll.id(), userId, null));
		PollResponseOption responseOption = pollResponseOptionRepository.saveAndFlush(
			PollResponseOption.create(response.id(), option.id())
		);
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campusId,
			PaymentCategory.MEAL,
			"밥 계좌",
			"테스트은행",
			"333-444",
			"밥 담당",
			userId
		));
		MealPollSettlement settlement = mealPollSettlementRepository.saveAndFlush(MealPollSettlement.create(
			campusId,
			poll.id(),
			account.id(),
			userId,
			1000,
			1000,
			0,
			endsAt
		));
		MealPollChargeGroup chargeGroup = mealPollChargeGroupRepository.saveAndFlush(MealPollChargeGroup.create(
			settlement.id(),
			poll.id(),
			option.id(),
			MealChargeCalculationType.PER_MEMBER,
			1000,
			1,
			1000,
			1000,
			1000,
			0
		));
		return new SettledMealPollGraph(
			poll.id(), option.id(), response.id(), responseOption.id(), settlement.id(), chargeGroup.id()
		);
	}

	private void installMealSettlementForeignKeysFromMigration() throws IOException {
		String migration = Files.readString(MEAL_SETTLEMENT_MIGRATION);
		String settlementPollCascade = migration.contains(
			"FOREIGN KEY (poll_id) REFERENCES polls (id) ON DELETE CASCADE"
		) ? " ON DELETE CASCADE" : "";
		String chargeGroupOptionCascade = migration.contains(
			"FOREIGN KEY (option_id) REFERENCES poll_options (id) ON DELETE CASCADE"
		) ? " ON DELETE CASCADE" : "";
		jdbcTemplate.execute("""
			ALTER TABLE meal_poll_settlements
			ADD CONSTRAINT fk_test_meal_poll_settlements_poll
			FOREIGN KEY (poll_id) REFERENCES polls (id)%s
			""".formatted(settlementPollCascade));
		jdbcTemplate.execute("""
			ALTER TABLE meal_poll_charge_groups
			ADD CONSTRAINT fk_test_meal_poll_charge_groups_option
			FOREIGN KEY (option_id) REFERENCES poll_options (id)%s
			""".formatted(chargeGroupOptionCascade));
	}

	private Poll savePoll(Long campusId, Instant endsAt) {
		Poll poll = Poll.create(
			campusId,
			null,
			"정리 대상 투표",
			PollType.CUSTOM,
			SelectionType.SINGLE,
			false,
			false,
			ChargeGenerationType.NONE,
			null,
			null,
			endsAt.minusSeconds(3600),
			endsAt,
			null
		);
		poll.open();
		return pollRepository.save(poll);
	}

	private PollComment saveComment(Long pollId, Long userId, boolean deleted, String deletedAt) {
		PollComment comment = pollCommentRepository.saveAndFlush(PollComment.create(pollId, userId, "댓글"));
		if (deleted) {
			comment.delete();
			pollCommentRepository.saveAndFlush(comment);
			jdbcTemplate.update(
				"update poll_comments set deleted_at = ? where id = ?",
				Instant.parse(deletedAt),
				comment.id()
			);
		}
		return comment;
	}

	private PrayerSubmission savePrayerSubmission(Long campusId, Long userId, LocalDate weekStartDate) {
		PrayerSeason season = prayerSeasonRepository.save(PrayerSeason.create(campusId, "기도 시즌", weekStartDate, userId));
		PrayerGroup group = prayerGroupRepository.save(PrayerGroup.create(season.id(), "1조", 1));
		PrayerWeek week = prayerWeekRepository.save(PrayerWeek.create(campusId, season.id(), weekStartDate));
		return prayerSubmissionRepository.saveAndFlush(PrayerSubmission.create(
			week.id(),
			group.id(),
			userId,
			"기도제목",
			userId,
			Instant.now()
		));
	}

	private WeeklyDevotionRecord saveWeeklyRecord(Long campusId, Long userId, LocalDate weekStartDate) {
		return weeklyRecordRepository.save(WeeklyDevotionRecord.create(campusId, userId, weekStartDate));
	}

	private DevotionDailyCheck saveDailyCheck(Long weeklyRecordId, LocalDate recordDate) {
		return dailyCheckRepository.save(DevotionDailyCheck.create(weeklyRecordId, recordDate, true, true, true));
	}

	private ChargeItem saveCharge(
		Long campusId,
		Long userId,
		Long accountId,
		Long sourceId,
		ChargeStatus status,
		String createdAt
	) {
		ChargeItem charge = ChargeItem.create(
			campusId,
			userId,
			PaymentCategory.PENALTY,
			accountId,
			"테스트은행",
			"111-222",
			"회계",
			ChargeSourceType.DEVOTION_RECORD,
			sourceId,
			"벌금",
			"사유",
			1000,
			null
		);
		if (status == ChargeStatus.PAID) {
			charge.markPaid(Instant.parse("2026-06-02T00:00:00Z"));
		} else if (status == ChargeStatus.WAIVED) {
			charge.waive();
		} else if (status == ChargeStatus.CANCELED) {
			charge.cancel();
		}
		ChargeItem saved = chargeItemRepository.saveAndFlush(charge);
		updateCreatedAt("charge_items", saved.id(), createdAt);
		return saved;
	}

	private User saveUser(String email) {
		return userRepository.save(User.create("정리테스트", email, "{noop}password"));
	}

	private Campus saveCampus(String suffix) {
		return campusRepository.saveAndFlush(Campus.create("정리캠퍼스", "서울", "테스트", "RET-" + suffix));
	}

	private void updateCreatedAt(String tableName, Long id, String createdAt) {
		jdbcTemplate.update("update " + tableName + " set created_at = ? where id = ?", Instant.parse(createdAt), id);
	}

	private record PollGraph(Long pollId, Long optionId, Long responseId, Long responseOptionId, Long commentId) {
	}

	private record SettledMealPollGraph(
		Long pollId,
		Long optionId,
		Long responseId,
		Long responseOptionId,
		Long settlementId,
		Long chargeGroupId
	) {
	}
}
