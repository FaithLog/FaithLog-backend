package com.faithlog.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.service.command.ChangeChargeStatusCommand;
import com.faithlog.billing.service.command.CompleteChargePaymentCommand;
import com.faithlog.billing.service.command.CreateCoffeeChargeCommand;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.service.command.CreatePenaltyChargeCommand;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.PenaltyRule;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.domain.type.PenaltyCalculationType;
import com.faithlog.devotion.domain.type.PenaltyRuleType;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.PenaltyRuleRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.devotion.infrastructure.adapter.BillingDevotionChargeReopenAdapter;
import com.faithlog.devotion.service.DevotionService;
import com.faithlog.devotion.service.command.DevotionDailyCheckCommand;
import com.faithlog.devotion.service.command.UpdateDailyDevotionCommand;
import com.faithlog.devotion.service.command.UpdateWeeklyDevotionCommand;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ChargeDevotionResubmissionIntegrationTest {

	private static final LocalDate WEEK_START = LocalDate.of(2026, 6, 15);

	@Autowired
	private BillingService billingService;

	@Autowired
	private DevotionService devotionService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WeeklyDevotionRecordRepository weeklyRecordRepository;

	@Autowired
	private DevotionDailyCheckRepository dailyCheckRepository;

	@Autowired
	private PenaltyRuleRepository penaltyRuleRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@MockitoSpyBean
	private BillingDevotionChargeReopenAdapter devotionChargeReopenAdapter;

	@Test
	void admin_marks_any_unpaid_charge_paid_with_server_time_and_rejects_terminal_targets() {
		User manager = saveUser("190-admin-paid-manager@example.com", UserRole.MANAGER);
		User member = saveUser("190-admin-paid-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "190관리자납부캠");
		joinCampus(campus, member);
		PaymentAccountResult coffeeAccount = createAccount(
			campus.campusId(), manager.id(), PaymentCategory.COFFEE, "190-COFFEE"
		);
		ChargeItemResult unpaid = billingService.createOrUpdateCoffeeCharge(new CreateCoffeeChargeCommand(
			campus.campusId(), member.id(), coffeeAccount.id(), 19001L,
			"커피 청구", "관리자 납부 완료", 2500, null
		));

		Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);
		ChargeItemResult paid = billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			unpaid.id(), manager.id(), ChargeStatus.PAID
		));
		Instant after = Instant.now().plus(1, ChronoUnit.SECONDS);

		assertThat(paid.status()).isEqualTo(ChargeStatus.PAID);
		assertThat(paid.paidAt()).isBetween(before, after);
		assertThat(chargeItemRepository.findById(unpaid.id())).get().satisfies(saved -> {
			assertThat(saved.status()).isEqualTo(ChargeStatus.PAID);
			assertThat(saved.paidAt()).isBetween(before, after);
		});

		PaymentAccountResult penaltyAccount = createAccount(
			campus.campusId(), manager.id(), PaymentCategory.PENALTY, "190-PENALTY-TERMINAL"
		);
		ChargeItem alreadyPaid = createPenaltyCharge(campus.campusId(), member.id(), penaltyAccount, 19002L);
		alreadyPaid.markPaid(Instant.parse("2026-07-13T01:00:00Z"));
		ChargeItem waived = createPenaltyCharge(campus.campusId(), member.id(), penaltyAccount, 19003L);
		waived.waive();
		ChargeItem canceled = createPenaltyCharge(campus.campusId(), member.id(), penaltyAccount, 19004L);
		canceled.cancel();
		chargeItemRepository.flush();

		for (ChargeItem terminal : List.of(alreadyPaid, waived, canceled)) {
			assertThatThrownBy(() -> billingService.changeChargeStatus(new ChangeChargeStatusCommand(
				terminal.id(), manager.id(), ChargeStatus.PAID
			)))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
					assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_CHARGE_STATUS_TRANSITION_CONFLICT)
				);
		}
	}

	@Test
	void canceling_devotion_penalty_reopens_weekly_record_and_preserves_daily_checks() {
		SubmittedDevotion fixture = submitPenaltyDevotion("190-cancel-reopen");
		List<DevotionDailyCheck> before = dailyCheckRepository
			.findByWeeklyRecordIdOrderByRecordDateAsc(fixture.weeklyRecord().id());

		ChargeItemResult canceled = billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			fixture.charge().id(), fixture.manager().id(), ChargeStatus.CANCELED
		));

		assertThat(canceled.status()).isEqualTo(ChargeStatus.CANCELED);
		assertThat(weeklyRecordRepository.findById(fixture.weeklyRecord().id()))
			.get()
			.extracting(WeeklyDevotionRecord::submittedAt)
			.isNull();
		assertThat(dailyCheckRepository.findByWeeklyRecordIdOrderByRecordDateAsc(fixture.weeklyRecord().id()))
			.usingRecursiveFieldByFieldElementComparatorIgnoringFields("updatedAt")
			.containsExactlyElementsOf(before);

		devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			fixture.campus().campusId(), fixture.member().id(), WEEK_START,
			false, true, true
		));
		assertThat(dailyCheckRepository.findByWeeklyRecordIdAndRecordDate(fixture.weeklyRecord().id(), WEEK_START))
			.get()
			.satisfies(check -> {
				assertThat(check.quietTimeChecked()).isFalse();
				assertThat(check.prayerChecked()).isTrue();
				assertThat(check.bibleReadingChecked()).isTrue();
			});
	}

	@Test
	void waived_penalty_and_canceled_coffee_charge_do_not_reopen_devotion() {
		SubmittedDevotion waivedFixture = submitPenaltyDevotion("190-waived-unaffected");
		billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			waivedFixture.charge().id(), waivedFixture.manager().id(), ChargeStatus.WAIVED
		));
		assertThat(weeklyRecordRepository.findById(waivedFixture.weeklyRecord().id()))
			.get()
			.extracting(WeeklyDevotionRecord::submittedAt)
			.isNotNull();

		SubmittedDevotion coffeeFixture = submitPenaltyDevotion("190-coffee-unaffected");
		PaymentAccountResult coffeeAccount = createAccount(
			coffeeFixture.campus().campusId(), coffeeFixture.manager().id(), PaymentCategory.COFFEE, "190-COFFEE-UNAFFECTED"
		);
		ChargeItemResult coffeeCharge = billingService.createOrUpdateCoffeeCharge(new CreateCoffeeChargeCommand(
			coffeeFixture.campus().campusId(), coffeeFixture.member().id(), coffeeAccount.id(),
			coffeeFixture.weeklyRecord().id(), "커피", "경건 source id와 우연히 같은 값", 2000, null
		));

		billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			coffeeCharge.id(), coffeeFixture.manager().id(), ChargeStatus.CANCELED
		));

		assertThat(weeklyRecordRepository.findById(coffeeFixture.weeklyRecord().id()))
			.get()
			.extracting(WeeklyDevotionRecord::submittedAt)
			.isNotNull();

		ChargeItem pollResponsePenalty = chargeItemRepository.saveAndFlush(ChargeItem.create(
			coffeeFixture.campus().campusId(),
			coffeeFixture.member().id(),
			PaymentCategory.PENALTY,
			coffeeFixture.account().id(),
			"하나은행",
			"190-POLL-RESPONSE-PENALTY",
			"회계",
			ChargeSourceType.POLL_RESPONSE,
			coffeeFixture.weeklyRecord().id(),
			"경건 source가 아닌 벌금",
			"POLL_RESPONSE source는 경건을 재오픈하지 않음",
			3000,
			WEEK_START.plusDays(7)
		));

		billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			pollResponsePenalty.id(), coffeeFixture.manager().id(), ChargeStatus.CANCELED
		));

		assertThat(weeklyRecordRepository.findById(coffeeFixture.weeklyRecord().id()))
			.get()
			.extracting(WeeklyDevotionRecord::submittedAt)
			.isNotNull();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void cross_campus_devotion_source_mismatch_rolls_back_charge_cancel() {
		SubmittedDevotion sourceFixture = submitPenaltyDevotion("190-source-campus");
		User otherManager = saveUser("190-mismatch-manager@example.com", UserRole.MANAGER);
		User otherMember = saveUser("190-mismatch-member@example.com", UserRole.USER);
		CampusCreateResult otherCampus = createCampus(otherManager, "190다른캠");
		joinCampus(otherCampus, otherMember);
		PaymentAccountResult otherAccount = createAccount(
			otherCampus.campusId(), otherManager.id(), PaymentCategory.PENALTY, "190-MISMATCH"
		);
		ChargeItem mismatched = createPenaltyCharge(
			otherCampus.campusId(), otherMember.id(), otherAccount, sourceFixture.weeklyRecord().id()
		);

		assertThatThrownBy(() -> billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			mismatched.id(), otherManager.id(), ChargeStatus.CANCELED
		)))
			.isInstanceOf(BusinessException.class);

		assertThat(chargeItemRepository.findById(mismatched.id()))
			.get()
			.extracting(ChargeItem::status)
			.isEqualTo(ChargeStatus.UNPAID);
		assertThat(weeklyRecordRepository.findById(sourceFixture.weeklyRecord().id()))
			.get()
			.extracting(WeeklyDevotionRecord::submittedAt)
			.isNotNull();
	}

	@Test
	void positive_resubmission_reuses_canceled_row_and_refreshes_amount_and_account_snapshot() {
		SubmittedDevotion fixture = submitPenaltyDevotion("190-positive-resubmit");
		long originalChargeCount = chargeItemRepository.count();
		billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			fixture.charge().id(), fixture.manager().id(), ChargeStatus.CANCELED
		));
		PaymentAccountResult replacement = createAccount(
			fixture.campus().campusId(), fixture.manager().id(), PaymentCategory.PENALTY, "190-REPLACEMENT"
		);
		PenaltyRule currentQuietTimeRule = penaltyRuleRepository
			.findByCampusIdOrderByIdAsc(fixture.campus().campusId())
			.stream()
			.filter(rule -> rule.ruleType() == PenaltyRuleType.QUIET_TIME)
			.findFirst()
			.orElseThrow();
		currentQuietTimeRule.update(5, 0, 700, true);
		penaltyRuleRepository.saveAndFlush(currentQuietTimeRule);

		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			fixture.campus().campusId(), fixture.member().id(), WEEK_START,
			List.of(new DevotionDailyCheckCommand(WEEK_START, true, true, true)),
			0, true
		));

		assertThat(chargeItemRepository.count()).isEqualTo(originalChargeCount);
		assertThat(chargeItemRepository.findById(fixture.charge().id())).get().satisfies(reused -> {
			assertThat(reused.status()).isEqualTo(ChargeStatus.UNPAID);
			assertThat(reused.amount()).isEqualTo(6000);
			assertThat(reused.paymentAccountId()).isEqualTo(replacement.id());
			assertThat(reused.accountNumberSnapshot()).isEqualTo("190-REPLACEMENT");
			assertThat(reused.paidAt()).isNull();
		});
	}

	@Test
	void zero_amount_resubmission_keeps_existing_row_canceled_without_creating_a_new_charge() {
		SubmittedDevotion fixture = submitPenaltyDevotion("190-zero-resubmit");
		long originalChargeCount = chargeItemRepository.count();
		billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			fixture.charge().id(), fixture.manager().id(), ChargeStatus.CANCELED
		));

		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			fixture.campus().campusId(), fixture.member().id(), WEEK_START,
			fullyCheckedWeekdays(), 0, true
		));

		assertThat(chargeItemRepository.count()).isEqualTo(originalChargeCount);
		assertThat(chargeItemRepository.findById(fixture.charge().id()))
			.get()
			.extracting(ChargeItem::status)
			.isEqualTo(ChargeStatus.CANCELED);
		assertThat(weeklyRecordRepository.findById(fixture.weeklyRecord().id()))
			.get()
			.extracting(WeeklyDevotionRecord::submittedAt)
			.isNotNull();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void failed_positive_resubmission_rolls_back_weekly_daily_and_canceled_charge() {
		SubmittedDevotion fixture = submitPenaltyDevotion("190-resubmit-rollback");
		billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			fixture.charge().id(), fixture.manager().id(), ChargeStatus.CANCELED
		));
		billingService.deactivatePaymentAccount(fixture.account().id(), fixture.manager().id());
		List<DevotionDailyCheck> before = dailyCheckRepository
			.findByWeeklyRecordIdOrderByRecordDateAsc(fixture.weeklyRecord().id());

		assertThatThrownBy(() -> devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			fixture.campus().campusId(), fixture.member().id(), WEEK_START,
			List.of(new DevotionDailyCheckCommand(WEEK_START, false, false, false)),
			9, true
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING)
			);

		assertThat(weeklyRecordRepository.findById(fixture.weeklyRecord().id()))
			.get()
			.extracting(WeeklyDevotionRecord::submittedAt)
			.isNull();
		assertThat(dailyCheckRepository.findByWeeklyRecordIdOrderByRecordDateAsc(fixture.weeklyRecord().id()))
			.usingRecursiveFieldByFieldElementComparatorIgnoringFields("updatedAt")
			.containsExactlyElementsOf(before);
		assertThat(chargeItemRepository.findById(fixture.charge().id()))
			.get()
			.extracting(ChargeItem::status)
			.isEqualTo(ChargeStatus.CANCELED);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void concurrent_admin_cancel_and_user_payment_allow_exactly_one_terminal_transition() throws Exception {
		SubmittedDevotion fixture = submitPenaltyDevotion("190-concurrent-status");
		CountDownLatch reopenEntered = new CountDownLatch(1);
		CountDownLatch allowReopen = new CountDownLatch(1);
		doAnswer(invocation -> {
			reopenEntered.countDown();
			if (!allowReopen.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out while coordinating concurrent charge status changes.");
			}
			return invocation.callRealMethod();
		}).when(devotionChargeReopenAdapter).reopenWeeklyDevotion(any(), any(), any());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ConcurrentStatusResult> cancelFuture = executor.submit(() -> captureStatusResult(() ->
				billingService.changeChargeStatus(new ChangeChargeStatusCommand(
					fixture.charge().id(), fixture.manager().id(), ChargeStatus.CANCELED
				))
			));
			assertThat(reopenEntered.await(5, TimeUnit.SECONDS)).isTrue();

			Future<ConcurrentStatusResult> paymentFuture = executor.submit(() -> captureStatusResult(() ->
				billingService.completeMyChargePayment(new CompleteChargePaymentCommand(
					fixture.campus().campusId(),
					fixture.charge().id(),
					fixture.member().id(),
					Instant.now()
				))
			));

			ConcurrentStatusResult earlyPayment = null;
			try {
				earlyPayment = paymentFuture.get(500, TimeUnit.MILLISECONDS);
			} catch (TimeoutException ignored) {
				// A locked second transition must wait for the cancel transaction to finish.
			}
			allowReopen.countDown();

			ConcurrentStatusResult cancelResult = cancelFuture.get(5, TimeUnit.SECONDS);
			ConcurrentStatusResult paymentResult = earlyPayment != null
				? earlyPayment
				: paymentFuture.get(5, TimeUnit.SECONDS);
			List<ConcurrentStatusResult> results = List.of(cancelResult, paymentResult);

			assertThat(results).filteredOn(ConcurrentStatusResult::succeeded).hasSize(1);
			assertThat(results)
				.filteredOn(result -> !result.succeeded())
				.extracting(ConcurrentStatusResult::errorCode)
				.containsExactly(ErrorCode.BILLING_MY_CHARGE_PAYMENT_CONFLICT);
			assertThat(chargeItemRepository.findById(fixture.charge().id()))
				.get()
				.extracting(ChargeItem::status)
				.isEqualTo(ChargeStatus.CANCELED);
			assertThat(weeklyRecordRepository.findById(fixture.weeklyRecord().id()))
				.get()
				.extracting(WeeklyDevotionRecord::submittedAt)
				.isNull();
		} finally {
			allowReopen.countDown();
			executor.shutdownNow();
		}
	}

	private ConcurrentStatusResult captureStatusResult(Supplier<ChargeItemResult> operation) {
		try {
			operation.get();
			return new ConcurrentStatusResult(true, null);
		} catch (BusinessException exception) {
			return new ConcurrentStatusResult(false, exception.errorCode());
		}
	}

	private SubmittedDevotion submitPenaltyDevotion(String prefix) {
		User manager = saveUser(prefix + "-manager@example.com", UserRole.MANAGER);
		User member = saveUser(prefix + "-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, prefix + "캠");
		joinCampus(campus, member);
		createPenaltyRules(campus.campusId());
		PaymentAccountResult account = createAccount(
			campus.campusId(), manager.id(), PaymentCategory.PENALTY, prefix + "-ACCOUNT"
		);
		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(), member.id(), WEEK_START,
			List.of(
				new DevotionDailyCheckCommand(WEEK_START, true, true, false),
				new DevotionDailyCheckCommand(WEEK_START.plusDays(1), true, false, true)
			),
			5, true
		));
		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(campus.campusId(), member.id(), WEEK_START)
			.orElseThrow();
		ChargeItem charge = chargeItemRepository
			.findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
				campus.campusId(), member.id(), PaymentCategory.PENALTY,
				ChargeSourceType.DEVOTION_RECORD, weeklyRecord.id()
			)
			.orElseThrow();
		return new SubmittedDevotion(manager, member, campus, account, weeklyRecord, charge);
	}

	private ChargeItem createPenaltyCharge(
		Long campusId,
		Long userId,
		PaymentAccountResult account,
		Long sourceId
	) {
		ChargeItemResult result = billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			campusId, userId, ChargeSourceType.DEVOTION_RECORD, sourceId,
			"경건생활 벌금", "테스트", 3000, WEEK_START.plusDays(7)
		));
		return chargeItemRepository.findById(result.id()).orElseThrow();
	}

	private CampusCreateResult createCampus(User manager, String name) {
		return campusService.createCampus(new CreateCampusCommand(manager.id(), name, "분당", name + " 설명"));
	}

	private void joinCampus(CampusCreateResult campus, User user) {
		campusService.joinCampus(new JoinCampusCommand(user.id(), campus.inviteCode()));
	}

	private PaymentAccountResult createAccount(
		Long campusId,
		Long managerId,
		PaymentCategory category,
		String accountNumber
	) {
		return billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId, managerId, category, category + " 계좌", "하나은행",
			accountNumber, "회계", managerId
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

	private List<DevotionDailyCheckCommand> fullyCheckedWeekdays() {
		return List.of(
			new DevotionDailyCheckCommand(WEEK_START, true, true, true),
			new DevotionDailyCheckCommand(WEEK_START.plusDays(1), true, true, true),
			new DevotionDailyCheckCommand(WEEK_START.plusDays(2), true, true, true),
			new DevotionDailyCheckCommand(WEEK_START.plusDays(3), true, true, true),
			new DevotionDailyCheckCommand(WEEK_START.plusDays(4), true, true, true)
		);
	}

	private User saveUser(String email, UserRole role) {
		User user = userRepository.saveAndFlush(User.create("190테스트", email, "encoded-password"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	private record SubmittedDevotion(
		User manager,
		User member,
		CampusCreateResult campus,
		PaymentAccountResult account,
		WeeklyDevotionRecord weeklyRecord,
		ChargeItem charge
	) {
	}

	private record ConcurrentStatusResult(boolean succeeded, ErrorCode errorCode) {
	}
}
