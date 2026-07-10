package com.faithlog.notification.service;

import com.faithlog.notification.service.command.RegisterFcmTokenCommand;
import com.faithlog.notification.service.command.SendNotificationCommand;
import com.faithlog.notification.service.result.SendNotificationResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.notification.domain.type.DeviceType;
import com.faithlog.notification.domain.entity.NotificationLog;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.domain.type.SendStatus;
import com.faithlog.notification.service.port.NotificationDispatchPort;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.support.NotificationConcurrencyTestConfig;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTest {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private FcmTokenService fcmTokenService;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private WeeklyDevotionRecordRepository weeklyDevotionRecordRepository;

	@Autowired
	private PollRepository pollRepository;

	@Autowired
	private PollResponseRepository pollResponseRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@MockitoBean
	private NotificationDispatchPort notificationDispatchPort;

	@Autowired
	private NotificationConcurrencyTestConfig.InMemoryNotificationConcurrencyPort notificationConcurrencyPort;

	@org.junit.jupiter.api.BeforeEach
	void resetNotificationConcurrencyPort() {
		notificationConcurrencyPort.reset();
	}

	@Test
	void requestNotification_validates_permission_and_creates_pending_or_skipped_logs_with_same_request_id() {
		Campus campus = saveCampus("알림캠A");
		User minister = saveUser("notification-minister@example.com", UserRole.USER);
		User memberWithToken = saveUser("notification-token-member@example.com", UserRole.USER);
		User memberWithoutToken = saveUser("notification-no-token-member@example.com", UserRole.USER);
		User normalMember = saveUser("notification-normal-member@example.com", UserRole.USER);
		User serviceManager = saveUser("notification-service-manager@example.com", UserRole.MANAGER);
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), memberWithToken.id());
		saveMember(campus.id(), memberWithoutToken.id());
		saveMember(campus.id(), normalMember.id());
		registerToken(memberWithToken, "notification-token-a", "notification-client-a");

		SendNotificationResult result = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.CUSTOM,
			List.of(memberWithToken.id(), memberWithoutToken.id()),
			null,
			null,
			"공지",
			"알림 본문"
		));

		assertThat(result.queuedCount()).isEqualTo(1);
		assertThat(result.skippedCount()).isEqualTo(1);
		List<NotificationLog> logs = notificationLogRepository.findByRequestIdOrderByIdAsc(result.notificationRequestId());
		assertThat(logs).hasSize(2);
		assertThat(logs).extracting(NotificationLog::requestId).containsOnly(result.notificationRequestId());
		assertThat(logs).extracting(NotificationLog::sendStatus).containsExactly(SendStatus.PENDING, SendStatus.SKIPPED);
		verify(notificationDispatchPort).dispatch(result.notificationRequestId());

		assertThatThrownBy(() -> notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			normalMember.id(),
			NotificationType.CUSTOM,
			List.of(memberWithToken.id()),
			null,
			null,
			"공지",
			"알림 본문"
		))).isInstanceOf(BusinessException.class)
			.hasMessage("알림 발송 권한이 없습니다.");

		assertThatThrownBy(() -> notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			serviceManager.id(),
			NotificationType.CUSTOM,
			List.of(memberWithToken.id()),
			null,
			null,
			"공지",
			"알림 본문"
		))).isInstanceOf(BusinessException.class)
			.hasMessage("알림 발송 권한이 없습니다.");
	}

	@Test
	void requestNotification_resolves_devotion_missing_targets_and_requires_custom_targets() {
		Campus campus = saveCampus("알림캠B");
		User minister = saveUser("notification-devotion-minister@example.com", UserRole.USER);
		User submitted = saveUser("notification-devotion-submitted@example.com", UserRole.USER);
		User missing = saveUser("notification-devotion-missing@example.com", UserRole.USER);
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), submitted.id());
		saveMember(campus.id(), missing.id());
		registerToken(submitted, "notification-submitted-token", "notification-submitted-client");
		registerToken(missing, "notification-missing-token", "notification-missing-client");
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);
		WeeklyDevotionRecord ministerRecord = WeeklyDevotionRecord.create(campus.id(), minister.id(), weekStartDate);
		ministerRecord.submit(Instant.now());
		weeklyDevotionRecordRepository.save(ministerRecord);
		WeeklyDevotionRecord submittedRecord = WeeklyDevotionRecord.create(campus.id(), submitted.id(), weekStartDate);
		submittedRecord.submit(Instant.now());
		weeklyDevotionRecordRepository.save(submittedRecord);

		SendNotificationResult result = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.DEVOTION_MISSING,
			null,
			weekStartDate,
			null,
			"경건생활 제출 알림",
			"이번 주 경건생활을 제출해 주세요."
		));

		List<NotificationLog> logs = notificationLogRepository.findByRequestIdOrderByIdAsc(result.notificationRequestId());
		assertThat(logs).extracting(NotificationLog::userId).containsExactly(missing.id());

		assertThatThrownBy(() -> notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.CUSTOM,
			null,
			null,
			null,
			"공지",
			"알림 본문"
		))).isInstanceOf(BusinessException.class)
			.hasMessage("알림 발송 대상이 필요합니다.");
	}

	@Test
	void requestNotification_resolves_poll_missing_and_payment_unpaid_targets() {
		Campus campus = saveCampus("알림캠D");
		User minister = saveUser("notification-auto-minister@example.com", UserRole.USER);
		User responded = saveUser("notification-auto-responded@example.com", UserRole.USER);
		User pollMissing = saveUser("notification-auto-poll-missing@example.com", UserRole.USER);
		User unpaid = saveUser("notification-auto-unpaid@example.com", UserRole.USER);
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), responded.id());
		saveMember(campus.id(), pollMissing.id());
		saveMember(campus.id(), unpaid.id());
		registerToken(pollMissing, "notification-poll-missing-token", "notification-poll-missing-client");
		registerToken(unpaid, "notification-unpaid-token", "notification-unpaid-client");
		Poll poll = pollRepository.saveAndFlush(Poll.create(
			campus.id(),
			null,
			"수요예배 참석 투표",
			PollType.WED_SERVICE,
			SelectionType.SINGLE,
			false,
			false,
			ChargeGenerationType.NONE,
			null,
			null,
			Instant.now().minusSeconds(60),
			Instant.now().plusSeconds(3600),
			minister.id()
		));
		pollResponseRepository.saveAndFlush(PollResponse.create(poll.id(), responded.id(), null));
		chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.id(),
			unpaid.id(),
			PaymentCategory.PENALTY,
			1L,
			"은행",
			"111",
			"회계",
			ChargeSourceType.DEVOTION_RECORD,
			9001L,
			"미납 청구",
			"테스트",
			1000,
			null
		));

		SendNotificationResult pollResult = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.WED_POLL_MISSING,
			null,
			null,
			poll.id(),
			"투표 알림",
			"투표에 참여해 주세요."
		));
		SendNotificationResult paymentResult = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.PAYMENT_UNPAID,
			null,
			null,
			null,
			"미납 알림",
			"미납 청구를 확인해 주세요."
		));

		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(pollResult.notificationRequestId()))
			.extracting(NotificationLog::userId)
			.contains(pollMissing.id())
			.doesNotContain(responded.id());
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(paymentResult.notificationRequestId()))
			.extracting(NotificationLog::userId)
			.containsExactly(unpaid.id());
	}

	@Test
	void manualAdminNotification_is_not_blocked_by_automatic_business_dedup() {
		Campus campus = saveCampus("알림캠G");
		User minister = saveUser("notification-manual-minister@example.com", UserRole.USER);
		User missing = saveUser("notification-manual-missing@example.com", UserRole.USER);
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), missing.id());
		registerToken(missing, "notification-manual-missing-token", "notification-manual-missing-client");
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);
		WeeklyDevotionRecord ministerRecord = WeeklyDevotionRecord.create(campus.id(), minister.id(), weekStartDate);
		ministerRecord.submit(Instant.now());
		weeklyDevotionRecordRepository.saveAndFlush(ministerRecord);

		SendNotificationResult first = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.DEVOTION_MISSING,
			null,
			weekStartDate,
			null,
			"경건생활 제출 알림",
			"이번 주 경건생활을 제출해 주세요."
		));
		SendNotificationResult second = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.DEVOTION_MISSING,
			null,
			weekStartDate,
			null,
			"경건생활 제출 알림",
			"이번 주 경건생활을 제출해 주세요."
		));

		assertThat(first.queuedCount()).isEqualTo(1);
		assertThat(second.queuedCount()).isEqualTo(1);
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(first.notificationRequestId())).hasSize(1);
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(second.notificationRequestId())).hasSize(1);
		assertThat(first.notificationRequestId()).isNotEqualTo(second.notificationRequestId());
	}

	private void registerToken(User user, String token, String clientInstanceId) {
		fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			token,
			clientInstanceId,
			DeviceType.IOS,
			"1.0.0"
		));
	}

	private Campus saveCampus(String name) {
		return campusRepository.saveAndFlush(Campus.create(name, "분당", name, "INV-" + name));
	}

	private CampusMember saveMinister(Long campusId, Long userId) {
		return campusMemberRepository.saveAndFlush(CampusMember.createMinister(campusId, userId));
	}

	private CampusMember saveMember(Long campusId, Long userId) {
		return campusMemberRepository.saveAndFlush(CampusMember.createMember(campusId, userId));
	}

	private User saveUser(String email, UserRole role) {
		User user = userRepository.save(User.create(email.substring(0, email.indexOf('@')), email, "{noop}password"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
