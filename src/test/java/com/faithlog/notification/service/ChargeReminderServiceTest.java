package com.faithlog.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.infrastructure.repository.PaymentAccountRepository;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.infrastructure.repository.CampusDutyAssignmentRepository;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.notification.domain.type.DeviceType;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.notification.service.command.RegisterFcmTokenCommand;
import com.faithlog.notification.service.port.NotificationDispatchPort;
import com.faithlog.notification.service.port.NotificationRedisOperationException;
import com.faithlog.notification.service.result.SendNotificationResult;
import com.faithlog.support.NotificationConcurrencyTestConfig;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChargeReminderServiceTest {

	@Autowired
	private ChargeReminderService chargeReminderService;

	@Autowired
	private FcmTokenService fcmTokenService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private CampusDutyAssignmentRepository dutyAssignmentRepository;

	@Autowired
	private PaymentAccountRepository paymentAccountRepository;

	@MockitoSpyBean
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private NotificationConcurrencyTestConfig.InMemoryNotificationConcurrencyPort concurrencyPort;

	@Autowired
	private CommitFailureInvoker commitFailureInvoker;

	@MockitoBean
	private NotificationDispatchPort notificationDispatchPort;

	@BeforeEach
	void resetConcurrencyState() {
		concurrencyPort.reset();
		notificationLogRepository.deleteAll();
	}

	@Test
	void dispatch_failure_rolls_back_pending_charge_reminder_logs() {
		User duty = userRepository.saveAndFlush(User.create(
			"커피담당", "notification-200-rollback-duty@example.com", "encoded"));
		User target = userRepository.saveAndFlush(User.create(
			"대상자", "notification-200-rollback-target@example.com", "encoded"));
		Campus campus = campusRepository.saveAndFlush(Campus.create(
			"200알림롤백캠", "분당", "알림 롤백", "INV-200-ROLLBACK"));
		campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), duty.id()));
		campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), target.id()));
		dutyAssignmentRepository.saveAndFlush(CampusDutyAssignment.assignCoffee(campus.id(), duty.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.id(), PaymentCategory.COFFEE, "롤백 계좌", "하나은행", "200-ROLLBACK", "커피담당", duty.id()
		));
		chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.id(), target.id(), PaymentCategory.COFFEE, account.id(), account.bankName(), account.accountNumber(),
			account.accountHolder(), ChargeSourceType.POLL_RESPONSE, 20041L, "커피 주문", "롤백 검증", 1800, null
		));
		fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			target.id(), "notification-200-rollback-token", "notification-200-rollback-client", DeviceType.IOS, "1.0.0"
		));
		doThrow(new IllegalStateException("dispatch failure")).when(notificationDispatchPort).dispatch(any());

		assertThatThrownBy(() -> chargeReminderService.requestCoffeeReminders(campus.id(), duty.id()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("dispatch failure");
		assertThat(notificationLogRepository.count()).isZero();

		reset(notificationDispatchPort);
		SendNotificationResult retried = chargeReminderService.requestCoffeeReminders(campus.id(), duty.id());
		assertThat(retried.queuedCount()).isEqualTo(1);
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(retried.notificationRequestId())).hasSize(1);
	}

	@Test
	void reminder_body_groups_same_titles_and_limits_details_to_five_distinct_items() {
		User duty = userRepository.saveAndFlush(User.create(
			"밥담당", "notification-200-details-duty@example.com", "encoded"));
		User target = userRepository.saveAndFlush(User.create(
			"대상자", "notification-200-details-target@example.com", "encoded"));
		Campus campus = campusRepository.saveAndFlush(Campus.create(
			"200알림상세캠", "분당", "알림 상세", "INV-200-DETAILS"));
		campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), duty.id()));
		campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), target.id()));
		dutyAssignmentRepository.saveAndFlush(CampusDutyAssignment.assignMeal(campus.id(), duty.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.id(), PaymentCategory.MEAL, "상세 계좌", "하나은행", "200-DETAILS", "밥담당", duty.id()
		));
		String[] titles = {"월요일 점심", "월요일 점심", "화요일 점심", "수요일 점심", "목요일 점심", "금요일 점심", "주일 점심"};
		int[] amounts = {3000, 4000, 5000, 6000, 7000, 8000, 9000};
		for (int index = 0; index < titles.length; index++) {
			chargeItemRepository.saveAndFlush(ChargeItem.create(
				campus.id(), target.id(), PaymentCategory.MEAL, account.id(), account.bankName(), account.accountNumber(),
				account.accountHolder(), ChargeSourceType.POLL_RESPONSE, 20100L + index, titles[index], "상세 검증",
				amounts[index], null
			));
		}
		fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			target.id(), "notification-200-details-token", "notification-200-details-client", DeviceType.IOS, "1.0.0"
		));

		SendNotificationResult result = chargeReminderService.requestMealReminders(campus.id(), duty.id());

		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(result.notificationRequestId()))
			.singleElement()
			.satisfies(log -> assertThat(log.body()).isEqualTo(
				"밥 미납: 월요일 점심 2건 7000원, 화요일 점심 1건 5000원, 수요일 점심 1건 6000원, "
					+ "목요일 점심 1건 7000원, 금요일 점심 1건 8000원, 외 1종 / 총 42000원입니다. 확인 후 납부해 주세요."
			));
	}

	@Test
	void reminder_includes_unpaid_charges_linked_to_soft_deleted_owned_account() {
		ReminderFixture fixture = createCoffeeReminderFixture("deleted-account");
		fixture.account().deactivate();
		fixture.account().softDelete();
		paymentAccountRepository.saveAndFlush(fixture.account());

		SendNotificationResult result = chargeReminderService.requestCoffeeReminders(
			fixture.campus().id(), fixture.duty().id()
		);

		assertThat(result.queuedCount()).isEqualTo(1);
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(result.notificationRequestId()))
			.singleElement()
			.satisfies(log -> assertThat(log.targetId()).isEqualTo(fixture.account().id()));
	}

	@Test
	void reminder_skips_charge_query_when_requester_has_no_owned_account() {
		User duty = userRepository.saveAndFlush(User.create(
			"커피담당", "notification-200-no-account-duty@example.com", "encoded"));
		Campus campus = campusRepository.saveAndFlush(Campus.create(
			"200소유계좌없음캠", "분당", "알림 조회 범위", "INV-200-NO-ACCOUNT"));
		campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), duty.id()));
		dutyAssignmentRepository.saveAndFlush(CampusDutyAssignment.assignCoffee(campus.id(), duty.id()));
		clearInvocations(chargeItemRepository);

		SendNotificationResult result = chargeReminderService.requestCoffeeReminders(campus.id(), duty.id());

		assertThat(result.queuedCount()).isZero();
		assertThat(result.skippedCount()).isZero();
		verify(chargeItemRepository, never()).findByCampusIdAndPaymentCategoryAndStatus(
			campus.id(), PaymentCategory.COFFEE, com.faithlog.billing.domain.type.ChargeStatus.UNPAID);
	}

	@Test
	void reminder_does_not_load_campus_wide_unpaid_charges_when_requester_owns_only_some_accounts() {
		ReminderFixture fixture = createCoffeeReminderFixture("owned-query-scope");
		User otherDuty = userRepository.saveAndFlush(User.create(
			"다른담당", "notification-200-owned-query-other@example.com", "encoded"));
		campusMemberRepository.saveAndFlush(CampusMember.createMember(fixture.campus().id(), otherDuty.id()));
		dutyAssignmentRepository.saveAndFlush(CampusDutyAssignment.assignCoffee(fixture.campus().id(), otherDuty.id()));
		PaymentAccount otherAccount = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			fixture.campus().id(), PaymentCategory.COFFEE, "다른 계좌", "하나은행", "200-OTHER-QUERY",
			"다른담당", otherDuty.id()));
		chargeItemRepository.saveAndFlush(ChargeItem.create(
			fixture.campus().id(), fixture.duty().id(), PaymentCategory.COFFEE, otherAccount.id(),
			otherAccount.bankName(), otherAccount.accountNumber(), otherAccount.accountHolder(),
			ChargeSourceType.POLL_RESPONSE, 20999L, "다른 담당 커피", "조회 제외", 2500, null));
		clearInvocations(chargeItemRepository);

		SendNotificationResult result = chargeReminderService.requestCoffeeReminders(
			fixture.campus().id(), fixture.duty().id());

		assertThat(result.queuedCount()).isEqualTo(1);
		verify(chargeItemRepository, never()).findByCampusIdAndPaymentCategoryAndStatus(
			fixture.campus().id(), PaymentCategory.COFFEE,
			com.faithlog.billing.domain.type.ChargeStatus.UNPAID);
	}

	@Test
	void transaction_commit_failure_releases_reserved_daily_dedupe_for_retry() {
		ReminderFixture fixture = createCoffeeReminderFixture("commit-failure");

		assertThatThrownBy(() -> commitFailureInvoker.requestCoffeeReminder(
			fixture.campus().id(), fixture.duty().id()
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("test commit failure");
		assertThat(notificationLogRepository.count()).isZero();

		SendNotificationResult retried = chargeReminderService.requestCoffeeReminders(
			fixture.campus().id(), fixture.duty().id()
		);
		assertThat(retried.queuedCount()).isEqualTo(1);
		assertThat(retried.skippedCount()).isZero();
	}

	@Test
	void manual_lock_release_failure_rolls_back_and_releases_reserved_daily_dedupe_for_retry() {
		ReminderFixture fixture = createCoffeeReminderFixture("release-failure");
		concurrencyPort.failLockRelease();

		assertThatThrownBy(() -> chargeReminderService.requestCoffeeReminders(
			fixture.campus().id(), fixture.duty().id()
		))
			.isInstanceOf(NotificationRedisOperationException.class)
			.hasMessage("test lock release failure");
		assertThat(notificationLogRepository.count()).isZero();

		concurrencyPort.allowLockRelease();
		SendNotificationResult retried = chargeReminderService.requestCoffeeReminders(
			fixture.campus().id(), fixture.duty().id()
		);
		assertThat(retried.queuedCount()).isEqualTo(1);
		assertThat(retried.skippedCount()).isZero();
	}

	private ReminderFixture createCoffeeReminderFixture(String suffix) {
		User duty = userRepository.saveAndFlush(User.create(
			"커피담당", "notification-200-" + suffix + "-duty@example.com", "encoded"));
		User target = userRepository.saveAndFlush(User.create(
			"대상자", "notification-200-" + suffix + "-target@example.com", "encoded"));
		Campus campus = campusRepository.saveAndFlush(Campus.create(
			"200" + suffix + "캠", "분당", "알림 트랜잭션 검증", "INV-200-" + suffix));
		campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), duty.id()));
		campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), target.id()));
		dutyAssignmentRepository.saveAndFlush(CampusDutyAssignment.assignCoffee(campus.id(), duty.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.id(), PaymentCategory.COFFEE, "알림 계좌", "하나은행", "200-" + suffix,
			"커피담당", duty.id()
		));
		chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.id(), target.id(), PaymentCategory.COFFEE, account.id(), account.bankName(), account.accountNumber(),
			account.accountHolder(), ChargeSourceType.POLL_RESPONSE, account.id(), "커피 주문", "알림 검증", 1800, null
		));
		fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			target.id(), "notification-200-" + suffix + "-token", "notification-200-" + suffix + "-client",
			DeviceType.IOS, "1.0.0"
		));
		return new ReminderFixture(campus, duty, account);
	}

	private record ReminderFixture(Campus campus, User duty, PaymentAccount account) {
	}

	@TestConfiguration
	static class CommitFailureConfig {

		@Bean
		CommitFailureInvoker commitFailureInvoker(ChargeReminderService chargeReminderService) {
			return new CommitFailureInvoker(chargeReminderService);
		}
	}

	static class CommitFailureInvoker {

		private final ChargeReminderService chargeReminderService;

		CommitFailureInvoker(ChargeReminderService chargeReminderService) {
			this.chargeReminderService = chargeReminderService;
		}

		@Transactional
		public SendNotificationResult requestCoffeeReminder(Long campusId, Long requesterId) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void beforeCommit(boolean readOnly) {
					throw new IllegalStateException("test commit failure");
				}
			});
			return chargeReminderService.requestCoffeeReminders(campusId, requesterId);
		}
	}
}
