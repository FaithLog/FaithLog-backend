package com.faithlog.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

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
import com.faithlog.notification.service.result.SendNotificationResult;
import com.faithlog.support.NotificationConcurrencyTestConfig;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private NotificationConcurrencyTestConfig.InMemoryNotificationConcurrencyPort concurrencyPort;

	@MockitoBean
	private NotificationDispatchPort notificationDispatchPort;

	@BeforeEach
	void resetConcurrencyState() {
		concurrencyPort.reset();
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
}
