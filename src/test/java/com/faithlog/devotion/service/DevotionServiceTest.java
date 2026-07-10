package com.faithlog.devotion.service;

import com.faithlog.devotion.service.command.DevotionDailyCheckCommand;
import com.faithlog.devotion.service.command.UpdateDailyDevotionCommand;
import com.faithlog.devotion.service.command.UpdateWeeklyDevotionCommand;
import com.faithlog.devotion.service.query.GetMissingDevotionMembersQuery;
import com.faithlog.devotion.service.query.GetMyMonthlyDevotionSummaryQuery;
import com.faithlog.devotion.service.query.GetMyWeeklyDevotionQuery;
import com.faithlog.devotion.service.result.DailyDevotionCheckResult;
import com.faithlog.devotion.service.result.DailyDevotionResult;
import com.faithlog.devotion.service.result.MissingDevotionMemberResult;
import com.faithlog.devotion.service.result.MyMonthlyDevotionSummaryResult;
import com.faithlog.devotion.service.result.WeeklyDevotionResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.billing.service.BillingService;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.type.PenaltyCalculationType;
import com.faithlog.devotion.domain.entity.PenaltyRule;
import com.faithlog.devotion.domain.type.PenaltyRuleType;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.PenaltyRuleRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DevotionServiceTest {

	@Autowired
	private DevotionService devotionService;

	@Autowired
	private DevotionMonthlySummaryQueryService monthlySummaryQueryService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private BillingService billingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private WeeklyDevotionRecordRepository weeklyRecordRepository;

	@Autowired
	private DevotionDailyCheckRepository dailyCheckRepository;

	@Autowired
	private PenaltyRuleRepository penaltyRuleRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Test
	void getMyMonthlySummary_aggregates_selected_month_daily_checks_and_saturday_late_by_saturday_date() {
		User manager = saveUser("devotion-monthly-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "90캠");
		User member = saveUser("devotion-monthly-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		LocalDate juneWeekStartDate = LocalDate.of(2026, 6, 22);
		LocalDate crossingWeekStartDate = LocalDate.of(2026, 6, 29);
		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			juneWeekStartDate,
			List.of(
				new DevotionDailyCheckCommand(LocalDate.of(2026, 6, 22), true, true, true),
				new DevotionDailyCheckCommand(LocalDate.of(2026, 6, 24), true, false, true),
				new DevotionDailyCheckCommand(LocalDate.of(2026, 6, 27), false, true, false)
			),
			12,
			false
		));
		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			crossingWeekStartDate,
			List.of(
				new DevotionDailyCheckCommand(LocalDate.of(2026, 6, 29), true, true, false),
				new DevotionDailyCheckCommand(LocalDate.of(2026, 6, 30), true, false, false),
				new DevotionDailyCheckCommand(LocalDate.of(2026, 7, 1), true, true, true),
				new DevotionDailyCheckCommand(LocalDate.of(2026, 7, 4), false, true, true)
			),
			9,
			false
		));

		MyMonthlyDevotionSummaryResult june = monthlySummaryQueryService.getMyMonthlySummary(
			new GetMyMonthlyDevotionSummaryQuery(campus.campusId(), member.id(), 2026, 6)
		);
		MyMonthlyDevotionSummaryResult july = monthlySummaryQueryService.getMyMonthlySummary(
			new GetMyMonthlyDevotionSummaryQuery(campus.campusId(), member.id(), 2026, 7)
		);

		assertThat(june.campusId()).isEqualTo(campus.campusId());
		assertThat(june.campusName()).isEqualTo("90캠");
		assertThat(june.region()).isEqualTo("분당");
		assertThat(june.userId()).isEqualTo(member.id());
		assertThat(june.name()).isEqualTo("경건테스트");
		assertThat(june.year()).isEqualTo(2026);
		assertThat(june.month()).isEqualTo(6);
		assertThat(june.devotion().quietTimeCount()).isEqualTo(4);
		assertThat(june.devotion().prayerCount()).isEqualTo(3);
		assertThat(june.devotion().bibleReadingCount()).isEqualTo(2);
		assertThat(june.devotion().saturdayLateMinutes()).isEqualTo(12);
		assertThat(june.weeklyRecords())
			.extracting(MyMonthlyDevotionSummaryResult.WeeklyRecord::weekStartDate)
			.containsExactly(juneWeekStartDate, crossingWeekStartDate);
		assertThat(june.weeklyRecords().get(0)).satisfies(week -> {
			assertThat(week.weekEndDate()).isEqualTo(LocalDate.of(2026, 6, 28));
			assertThat(week.quietTimeCount()).isEqualTo(2);
			assertThat(week.prayerCount()).isEqualTo(2);
			assertThat(week.bibleReadingCount()).isEqualTo(2);
			assertThat(week.saturdayLateMinutes()).isEqualTo(12);
		});
		assertThat(june.weeklyRecords().get(1)).satisfies(week -> {
			assertThat(week.weekEndDate()).isEqualTo(LocalDate.of(2026, 7, 5));
			assertThat(week.quietTimeCount()).isEqualTo(2);
			assertThat(week.prayerCount()).isEqualTo(1);
			assertThat(week.bibleReadingCount()).isZero();
			assertThat(week.saturdayLateMinutes()).isZero();
		});

		assertThat(july.devotion().quietTimeCount()).isEqualTo(1);
		assertThat(july.devotion().prayerCount()).isEqualTo(2);
		assertThat(july.devotion().bibleReadingCount()).isEqualTo(2);
		assertThat(july.devotion().saturdayLateMinutes()).isEqualTo(9);
		assertThat(july.weeklyRecords()).singleElement().satisfies(week -> {
			assertThat(week.weekStartDate()).isEqualTo(crossingWeekStartDate);
			assertThat(week.quietTimeCount()).isEqualTo(1);
			assertThat(week.prayerCount()).isEqualTo(2);
			assertThat(week.bibleReadingCount()).isEqualTo(2);
			assertThat(week.saturdayLateMinutes()).isEqualTo(9);
		});
	}

	@Test
	void getMyMonthlySummary_rejects_invalid_year_month_and_requires_active_campus_member() {
		User manager = saveUser("devotion-monthly-auth-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "91캠");
		User member = saveUser("devotion-monthly-auth-member@example.com", UserRole.USER);
		User outsider = saveUser("devotion-monthly-auth-outsider@example.com", UserRole.USER);
		joinCampus(campus, member);

		assertThatThrownBy(() -> monthlySummaryQueryService.getMyMonthlySummary(
			new GetMyMonthlyDevotionSummaryQuery(campus.campusId(), member.id(), 2026, 13)
		))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_INVALID_YEAR_MONTH)
			)
			.hasMessage("조회 연월이 올바르지 않습니다.");

		assertThatThrownBy(() -> monthlySummaryQueryService.getMyMonthlySummary(
			new GetMyMonthlyDevotionSummaryQuery(campus.campusId(), member.id(), 0, 6)
		))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_INVALID_YEAR_MONTH)
			)
			.hasMessage("조회 연월이 올바르지 않습니다.");

		assertThatThrownBy(() -> monthlySummaryQueryService.getMyMonthlySummary(
			new GetMyMonthlyDevotionSummaryQuery(campus.campusId(), member.id(), -1, 6)
		))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_INVALID_YEAR_MONTH)
			)
			.hasMessage("조회 연월이 올바르지 않습니다.");

		assertThatThrownBy(() -> monthlySummaryQueryService.getMyMonthlySummary(
			new GetMyMonthlyDevotionSummaryQuery(campus.campusId(), outsider.id(), 2026, 6)
		))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_ACCESS_FORBIDDEN)
			)
			.hasMessage("경건생활 접근 권한이 없습니다.");
	}

	@Test
	void updateDailyCheck_creates_daily_and_weekly_rows_without_submission_or_charge() {
		User manager = saveUser("devotion-daily-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "60캠");
		User member = saveUser("devotion-daily-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		LocalDate recordDate = LocalDate.of(2026, 6, 17);

		DailyDevotionResult result = devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			campus.campusId(),
			member.id(),
			recordDate,
			true,
			true,
			false
		));

		assertThat(result.recordDate()).isEqualTo(recordDate);
		assertThat(result.quietTimeChecked()).isTrue();
		assertThat(result.prayerChecked()).isTrue();
		assertThat(result.bibleReadingChecked()).isFalse();
		assertThat(result.quietTimeCount()).isEqualTo(1);
		assertThat(result.prayerCount()).isEqualTo(1);
		assertThat(result.bibleReadingCount()).isEqualTo(0);
		assertThat(result.submittedAt()).isNull();
		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(campus.campusId(), member.id(), LocalDate.of(2026, 6, 15))
			.orElseThrow();
		assertThat(weeklyRecord.submittedAt()).isNull();
		assertThat(dailyCheckRepository.findByWeeklyRecordIdAndRecordDate(weeklyRecord.id(), recordDate)).isPresent();
		assertThat(chargeItemRepository.count()).isZero();

		devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			campus.campusId(),
			member.id(),
			recordDate,
			false,
			true,
			true
		));

		assertThat(dailyCheckRepository.findByWeeklyRecordIdAndRecordDate(weeklyRecord.id(), recordDate))
			.get()
			.satisfies(check -> {
				assertThat(check.quietTimeChecked()).isFalse();
				assertThat(check.prayerChecked()).isTrue();
				assertThat(check.bibleReadingChecked()).isTrue();
			});
		assertThat(weeklyRecordRepository.findById(weeklyRecord.id()))
			.get()
			.satisfies(saved -> {
				assertThat(saved.submittedAt()).isNull();
				assertThat(saved.quietTimeCount()).isZero();
				assertThat(saved.prayerCount()).isEqualTo(1);
				assertThat(saved.bibleReadingCount()).isEqualTo(1);
			});
		assertThat(chargeItemRepository.count()).isZero();
	}

	@Test
	void updateWeeklyCheck_creates_seven_daily_rows_fills_missing_false_and_updates_submission_summary() {
		User manager = saveUser("devotion-weekly-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "61캠");
		User member = saveUser("devotion-weekly-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		createPenaltyRules(campus.campusId());
		createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-100");
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		WeeklyDevotionResult result = devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(
				new DevotionDailyCheckCommand(weekStartDate, true, true, false),
				new DevotionDailyCheckCommand(weekStartDate.plusDays(2), true, false, true)
			),
			5,
			true
		));

		assertThat(result.weekStartDate()).isEqualTo(weekStartDate);
		assertThat(result.weekEndDate()).isEqualTo(LocalDate.of(2026, 6, 21));
		assertThat(result.quietTimeCount()).isEqualTo(2);
		assertThat(result.prayerCount()).isEqualTo(1);
		assertThat(result.bibleReadingCount()).isEqualTo(1);
		assertThat(result.saturdayLateMinutes()).isEqualTo(5);
		assertThat(result.submittedAt()).isNotNull();
		assertThat(result.dailyChecks()).hasSize(7);

		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(campus.campusId(), member.id(), weekStartDate)
			.orElseThrow();
		List<DevotionDailyCheck> dailyChecks = dailyCheckRepository.findByWeeklyRecordIdOrderByRecordDateAsc(weeklyRecord.id());
		assertThat(dailyChecks).hasSize(7);
		assertThat(dailyChecks)
			.filteredOn(check -> check.recordDate().equals(weekStartDate.plusDays(1)))
			.singleElement()
			.satisfies(check -> {
				assertThat(check.quietTimeChecked()).isFalse();
				assertThat(check.prayerChecked()).isFalse();
				assertThat(check.bibleReadingChecked()).isFalse();
			});
		assertThat(chargeItemRepository.count()).isEqualTo(1);
	}

	@Test
	void updateWeeklyCheck_first_submit_creates_one_penalty_charge_with_weekly_record_source_and_account_snapshot() {
		User manager = saveUser("devotion-charge-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "69캠");
		User member = saveUser("devotion-charge-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		createPenaltyRules(campus.campusId());
		PaymentAccountResult account = createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-101");
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		WeeklyDevotionResult result = devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(
				new DevotionDailyCheckCommand(weekStartDate, true, true, false),
				new DevotionDailyCheckCommand(weekStartDate.plusDays(1), true, false, true)
			),
			5,
			true
		));

		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(campus.campusId(), member.id(), weekStartDate)
			.orElseThrow();
		assertThat(result.weeklyRecordId()).isEqualTo(weeklyRecord.id());
		assertThat(chargeItemRepository.count()).isEqualTo(1);
		assertThat(chargeItemRepository.findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
			campus.campusId(),
			member.id(),
			PaymentCategory.PENALTY,
			ChargeSourceType.DEVOTION_RECORD,
			weeklyRecord.id()
		))
			.get()
			.satisfies(charge -> {
				assertThat(charge.paymentAccountId()).isEqualTo(account.id());
				assertThat(charge.bankNameSnapshot()).isEqualTo("하나은행");
				assertThat(charge.accountNumberSnapshot()).isEqualTo("123-456789-101");
				assertThat(charge.accountHolderSnapshot()).isEqualTo("벌금회계");
				assertThat(charge.paymentCategory()).isEqualTo(PaymentCategory.PENALTY);
				assertThat(charge.sourceType()).isEqualTo(ChargeSourceType.DEVOTION_RECORD);
				assertThat(charge.sourceId()).isEqualTo(weeklyRecord.id());
				assertThat(charge.status()).isEqualTo(ChargeStatus.UNPAID);
				assertThat(charge.amount()).isEqualTo(6200);
			});
	}

	@Test
	void updateWeeklyCheck_zero_penalty_submit_does_not_create_charge() {
		User manager = saveUser("devotion-zero-charge-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "75캠");
		User member = saveUser("devotion-zero-charge-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		createPenaltyRules(campus.campusId());
		createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-107");
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		WeeklyDevotionResult result = devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			fullyCheckedWeekdays(weekStartDate),
			0,
			true
		));

		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(campus.campusId(), member.id(), weekStartDate)
			.orElseThrow();
		assertThat(result.submittedAt()).isNotNull();
		assertThat(result.weeklyRecordId()).isEqualTo(weeklyRecord.id());
		assertThat(chargeItemRepository.count()).isZero();
		assertThat(chargeItemRepository.findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
			campus.campusId(),
			member.id(),
			PaymentCategory.PENALTY,
			ChargeSourceType.DEVOTION_RECORD,
			weeklyRecord.id()
		)).isEmpty();
	}

	@Test
	void updateWeeklyCheck_zero_penalty_submit_succeeds_without_active_penalty_account() {
		User manager = saveUser("devotion-zero-no-account-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "76캠");
		User member = saveUser("devotion-zero-no-account-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		createPenaltyRules(campus.campusId());
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		WeeklyDevotionResult result = devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			fullyCheckedWeekdays(weekStartDate),
			0,
			true
		));

		assertThat(result.submittedAt()).isNotNull();
		assertThat(result.quietTimeCount()).isEqualTo(5);
		assertThat(result.prayerCount()).isEqualTo(5);
		assertThat(result.bibleReadingCount()).isEqualTo(5);
		assertThat(chargeItemRepository.count()).isZero();
		assertThat(weeklyRecordRepository.findByCampusIdAndUserIdAndWeekStartDate(
			campus.campusId(),
			member.id(),
			weekStartDate
		))
			.get()
			.satisfies(weeklyRecord -> assertThat(weeklyRecord.submittedAt()).isNotNull());
	}

	@Test
	void updateWeeklyCheck_submit_false_does_not_create_or_update_penalty_charge() {
		User manager = saveUser("devotion-draft-charge-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "70캠");
		User member = saveUser("devotion-draft-charge-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		createPenaltyRules(campus.campusId());
		createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-102");
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		WeeklyDevotionResult result = devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate, false, false, false)),
			3,
			false
		));

		assertThat(result.submittedAt()).isNull();
		assertThat(chargeItemRepository.count()).isZero();
		assertThat(weeklyRecordRepository.findByCampusIdAndUserIdAndWeekStartDate(
			campus.campusId(),
			member.id(),
			weekStartDate
		))
			.get()
			.satisfies(weeklyRecord -> assertThat(weeklyRecord.submittedAt()).isNull());
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void updateWeeklyCheck_without_active_penalty_account_fails_whole_submission_without_submittedAt_or_charge() {
		User manager = saveUser("devotion-no-account-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "71캠");
		User member = saveUser("devotion-no-account-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		createPenaltyRules(campus.campusId());
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		assertThatThrownBy(() -> devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate, true, true, true)),
			0,
			true
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			)
			.hasMessage("관리자에게 문의하세요");

		assertThat(chargeItemRepository.count()).isZero();
		assertThat(weeklyRecordRepository.findByCampusIdAndUserIdAndWeekStartDate(
			campus.campusId(),
			member.id(),
			weekStartDate
		)).isEmpty();
		assertThat(dailyCheckRepository.count()).isZero();
	}

	@Test
	void updateWeeklyCheck_rejects_duplicate_submit_after_weekly_record_was_submitted() {
		User manager = saveUser("devotion-duplicate-submit-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "72캠");
		User member = saveUser("devotion-duplicate-submit-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		createPenaltyRules(campus.campusId());
		createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-104");
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);
		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate, true, true, true)),
			0,
			true
		));

		assertThatThrownBy(() -> devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate, false, false, false)),
			10,
			true
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_WEEKLY_ALREADY_SUBMITTED)
			)
			.hasMessage("이미 제출된 주간 경건생활은 수정할 수 없습니다.");
		assertThat(chargeItemRepository.count()).isEqualTo(1);
	}

	@Test
	void updateWeeklyCheck_rejects_draft_save_after_weekly_record_was_submitted() {
		User manager = saveUser("devotion-after-submit-draft-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "73캠");
		User member = saveUser("devotion-after-submit-draft-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		createPenaltyRules(campus.campusId());
		createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-105");
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);
		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate, true, true, true)),
			0,
			true
		));

		assertThatThrownBy(() -> devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate.plusDays(1), false, false, false)),
			7,
			false
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_WEEKLY_ALREADY_SUBMITTED)
			)
			.hasMessage("이미 제출된 주간 경건생활은 수정할 수 없습니다.");
		assertThat(chargeItemRepository.count()).isEqualTo(1);
	}

	@Test
	void updateDailyCheck_rejects_same_week_change_after_weekly_record_was_submitted_without_mutating_rows_or_charge() {
		User manager = saveUser("devotion-daily-after-submit-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "74캠");
		User member = saveUser("devotion-daily-after-submit-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		createPenaltyRules(campus.campusId());
		createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-106");
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);
		LocalDate recordDate = weekStartDate.plusDays(2);
		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(recordDate, true, true, true)),
			0,
			true
		));
		WeeklyDevotionRecord submittedWeeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(campus.campusId(), member.id(), weekStartDate)
			.orElseThrow();
		List<DevotionDailyCheck> dailyChecksBefore = dailyCheckRepository
			.findByWeeklyRecordIdOrderByRecordDateAsc(submittedWeeklyRecord.id());
		long weeklyRecordCountBefore = weeklyRecordRepository.count();
		long dailyCheckCountBefore = dailyCheckRepository.count();
		long chargeCountBefore = chargeItemRepository.count();

		assertThatThrownBy(() -> devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			campus.campusId(),
			member.id(),
			recordDate,
			false,
			false,
			false
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_WEEKLY_ALREADY_SUBMITTED)
			)
			.hasMessage("이미 제출된 주간 경건생활은 수정할 수 없습니다.");

		assertThat(weeklyRecordRepository.count()).isEqualTo(weeklyRecordCountBefore);
		assertThat(dailyCheckRepository.count()).isEqualTo(dailyCheckCountBefore);
		assertThat(chargeItemRepository.count()).isEqualTo(chargeCountBefore);
		assertThat(weeklyRecordRepository.findById(submittedWeeklyRecord.id()))
			.get()
			.satisfies(weeklyRecord -> {
				assertThat(weeklyRecord.submittedAt()).isEqualTo(submittedWeeklyRecord.submittedAt());
				assertThat(weeklyRecord.quietTimeCount()).isEqualTo(submittedWeeklyRecord.quietTimeCount());
				assertThat(weeklyRecord.prayerCount()).isEqualTo(submittedWeeklyRecord.prayerCount());
				assertThat(weeklyRecord.bibleReadingCount()).isEqualTo(submittedWeeklyRecord.bibleReadingCount());
				assertThat(weeklyRecord.saturdayLateMinutes()).isEqualTo(submittedWeeklyRecord.saturdayLateMinutes());
			});
		assertThat(dailyCheckRepository.findByWeeklyRecordIdOrderByRecordDateAsc(submittedWeeklyRecord.id()))
			.usingRecursiveFieldByFieldElementComparatorIgnoringFields("updatedAt")
			.containsExactlyElementsOf(dailyChecksBefore);
	}

	@Test
	void getMyWeeklyCheck_uses_requester_identity_and_adminMissing_uses_submittedAt() {
		User manager = saveUser("devotion-missing-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "62캠");
		User submittedMember = saveUser("devotion-submitted-member@example.com", UserRole.USER);
		User unsubmittedMember = saveUser("devotion-unsubmitted-member@example.com", UserRole.USER);
		User noRecordMember = saveUser("devotion-no-record-member@example.com", UserRole.USER);
		joinCampus(campus, submittedMember);
		joinCampus(campus, unsubmittedMember);
		joinCampus(campus, noRecordMember);
		createPenaltyRules(campus.campusId());
		createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-103");
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);
		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			manager.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate, true, true, true)),
			0,
			true
		));
		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			submittedMember.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate, true, true, true)),
			0,
			true
		));
		devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			campus.campusId(),
			unsubmittedMember.id(),
			weekStartDate,
			true,
			true,
			true
		));

		WeeklyDevotionResult myWeek = devotionService.getMyWeeklyCheck(new GetMyWeeklyDevotionQuery(
			campus.campusId(),
			submittedMember.id(),
			weekStartDate
		));
		List<MissingDevotionMemberResult> missingMembers = devotionService.getMissingMembers(new GetMissingDevotionMembersQuery(
			campus.campusId(),
			manager.id(),
			weekStartDate
		));

		assertThat(myWeek.userId()).isEqualTo(submittedMember.id());
		assertThat(myWeek.submittedAt()).isNotNull();
		assertThat(missingMembers)
			.extracting(MissingDevotionMemberResult::userId)
			.containsExactly(unsubmittedMember.id(), noRecordMember.id());
	}

	@Test
	void getMyWeeklyCheck_returns_default_week_without_creating_rows_when_record_is_missing() {
		User manager = saveUser("devotion-empty-week-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "67캠");
		User member = saveUser("devotion-empty-week-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		WeeklyDevotionResult result = devotionService.getMyWeeklyCheck(new GetMyWeeklyDevotionQuery(
			campus.campusId(),
			member.id(),
			weekStartDate
		));

		assertThat(result.weeklyRecordId()).isNull();
		assertThat(result.userId()).isEqualTo(member.id());
		assertThat(result.weekStartDate()).isEqualTo(weekStartDate);
		assertThat(result.weekEndDate()).isEqualTo(LocalDate.of(2026, 6, 21));
		assertThat(result.quietTimeCount()).isZero();
		assertThat(result.prayerCount()).isZero();
		assertThat(result.bibleReadingCount()).isZero();
		assertThat(result.saturdayLateMinutes()).isZero();
		assertThat(result.submittedAt()).isNull();
		assertThat(result.dailyChecks())
			.hasSize(7)
			.allSatisfy(check -> {
				assertThat(check.id()).isNull();
				assertThat(check.quietTimeChecked()).isFalse();
				assertThat(check.prayerChecked()).isFalse();
				assertThat(check.bibleReadingChecked()).isFalse();
			})
			.extracting(DailyDevotionCheckResult::recordDate)
			.containsExactly(
				weekStartDate,
				weekStartDate.plusDays(1),
				weekStartDate.plusDays(2),
				weekStartDate.plusDays(3),
				weekStartDate.plusDays(4),
				weekStartDate.plusDays(5),
				weekStartDate.plusDays(6)
			);
		assertThat(weeklyRecordRepository.findByCampusIdAndUserIdAndWeekStartDate(
			campus.campusId(),
			member.id(),
			weekStartDate
		)).isEmpty();
		assertThat(dailyCheckRepository.count()).isZero();
	}

	@Test
	void getMyWeeklyCheck_merges_partial_daily_rows_into_seven_day_week_without_creating_missing_rows() {
		User manager = saveUser("devotion-partial-week-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "68캠");
		User member = saveUser("devotion-partial-week-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);
		LocalDate checkedDate = LocalDate.of(2026, 6, 17);
		devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			campus.campusId(),
			member.id(),
			checkedDate,
			true,
			true,
			false
		));
		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(campus.campusId(), member.id(), weekStartDate)
			.orElseThrow();
		long dailyCountBefore = dailyCheckRepository.count();

		WeeklyDevotionResult result = devotionService.getMyWeeklyCheck(new GetMyWeeklyDevotionQuery(
			campus.campusId(),
			member.id(),
			weekStartDate
		));

		assertThat(result.weeklyRecordId()).isEqualTo(weeklyRecord.id());
		assertThat(result.dailyChecks()).hasSize(7);
		assertThat(result.dailyChecks())
			.extracting(DailyDevotionCheckResult::recordDate)
			.containsExactly(
				weekStartDate,
				weekStartDate.plusDays(1),
				checkedDate,
				weekStartDate.plusDays(3),
				weekStartDate.plusDays(4),
				weekStartDate.plusDays(5),
				weekStartDate.plusDays(6)
			);
		assertThat(result.dailyChecks())
			.filteredOn(check -> check.recordDate().equals(checkedDate))
			.singleElement()
			.satisfies(check -> {
				assertThat(check.id()).isNotNull();
				assertThat(check.quietTimeChecked()).isTrue();
				assertThat(check.prayerChecked()).isTrue();
				assertThat(check.bibleReadingChecked()).isFalse();
			});
		assertThat(result.dailyChecks())
			.filteredOn(check -> check.recordDate().equals(weekStartDate))
			.singleElement()
			.satisfies(check -> {
				assertThat(check.id()).isNull();
				assertThat(check.quietTimeChecked()).isFalse();
				assertThat(check.prayerChecked()).isFalse();
				assertThat(check.bibleReadingChecked()).isFalse();
			});
		assertThat(dailyCheckRepository.count()).isEqualTo(dailyCountBefore);
	}

	@Test
	void weeklyCheck_rejects_non_monday_and_devotion_apis_require_active_campus_member() {
		User manager = saveUser("devotion-auth-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "63캠");
		User member = saveUser("devotion-auth-member@example.com", UserRole.USER);
		User outsider = saveUser("devotion-auth-outsider@example.com", UserRole.USER);
		joinCampus(campus, member);

		assertThatThrownBy(() -> devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			LocalDate.of(2026, 6, 16),
			List.of(),
			0,
			true
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("weekStartDate는 월요일이어야 합니다.");

		assertThatThrownBy(() -> devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			campus.campusId(),
			outsider.id(),
			LocalDate.of(2026, 6, 17),
			true,
			true,
			true
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("경건생활 접근 권한이 없습니다.");
	}

	@Test
	void updateWeeklyCheck_rejects_daily_check_recordDate_outside_requested_week() {
		User manager = saveUser("devotion-out-of-week-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "65캠");
		User member = saveUser("devotion-out-of-week-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		assertThatThrownBy(() -> devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate.plusDays(7), true, true, true)),
			0,
			true
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_DAILY_CHECK_DATE_OUT_OF_WEEK)
			)
			.hasMessage("dailyChecks[].recordDate는 요청 주차 안의 날짜여야 합니다.");
	}

	@Test
	void updateWeeklyCheck_rejects_negative_saturday_late_minutes() {
		User manager = saveUser("devotion-negative-late-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "66캠");
		User member = saveUser("devotion-negative-late-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		assertThatThrownBy(() -> devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate, true, true, true)),
			-1,
			true
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.DEVOTION_INVALID_SATURDAY_LATE_MINUTES)
			)
			.hasMessage("saturdayLateMinutes는 0 이상이어야 합니다.");
	}

	@Test
	void adminMissing_requires_campus_manager_or_service_admin() {
		User manager = saveUser("devotion-admin-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "64캠");
		User normalMember = saveUser("devotion-admin-normal@example.com", UserRole.USER);
		User leader = saveUser("devotion-admin-leader@example.com", UserRole.USER);
		User admin = saveUser("devotion-admin-service@example.com", UserRole.ADMIN);
		joinCampus(campus, normalMember);
		joinCampus(campus, leader);
		updateCampusRole(campus.campusId(), leader.id(), CampusRole.CAMPUS_LEADER);
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		assertThatThrownBy(() -> devotionService.getMissingMembers(new GetMissingDevotionMembersQuery(
			campus.campusId(),
			normalMember.id(),
			weekStartDate
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("경건생활 관리자 권한이 없습니다.");

		assertThat(devotionService.getMissingMembers(new GetMissingDevotionMembersQuery(
			campus.campusId(),
			leader.id(),
			weekStartDate
		))).isNotEmpty();
		assertThat(devotionService.getMissingMembers(new GetMissingDevotionMembersQuery(
			campus.campusId(),
			admin.id(),
			weekStartDate
		))).isNotEmpty();
	}

	private CampusCreateResult createCampus(User manager, String name) {
		return campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			name,
			"분당",
			"분당 " + name
		));
	}

	private void joinCampus(CampusCreateResult campus, User user) {
		campusService.joinCampus(new JoinCampusCommand(user.id(), campus.inviteCode()));
	}

	private PaymentAccountResult createPenaltyAccount(Long campusId, Long managerId, String accountNumber) {
		return billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId,
			managerId,
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"하나은행",
			accountNumber,
			"벌금회계",
			null
		));
	}

	private void createPenaltyRules(Long campusId) {
		penaltyRuleRepository.saveAllAndFlush(List.of(
			PenaltyRule.create(campusId, PenaltyRuleType.QUIET_TIME, PenaltyCalculationType.MISSING_COUNT, 5, 0, 500),
			PenaltyRule.create(campusId, PenaltyRuleType.PRAYER, PenaltyCalculationType.MISSING_COUNT, 5, 0, 500),
			PenaltyRule.create(campusId, PenaltyRuleType.BIBLE_READING, PenaltyCalculationType.MISSING_COUNT, 5, 0, 300),
			PenaltyRule.create(campusId, PenaltyRuleType.SATURDAY_LATE, PenaltyCalculationType.LATE_MINUTE, 0, 1000, 100)
		));
	}

	private List<DevotionDailyCheckCommand> fullyCheckedWeekdays(LocalDate weekStartDate) {
		return List.of(
			new DevotionDailyCheckCommand(weekStartDate, true, true, true),
			new DevotionDailyCheckCommand(weekStartDate.plusDays(1), true, true, true),
			new DevotionDailyCheckCommand(weekStartDate.plusDays(2), true, true, true),
			new DevotionDailyCheckCommand(weekStartDate.plusDays(3), true, true, true),
			new DevotionDailyCheckCommand(weekStartDate.plusDays(4), true, true, true)
		);
	}

	private User saveUser(String email, UserRole role) {
		User user = userRepository.saveAndFlush(User.create("경건테스트", email, "encoded-password"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	private void updateCampusRole(Long campusId, Long userId, CampusRole campusRole) {
		CampusMember member = campusMemberRepository.findByCampusIdAndUserId(campusId, userId).orElseThrow();
		ReflectionTestUtils.setField(member, "campusRole", campusRole);
		campusMemberRepository.saveAndFlush(member);
	}
}
