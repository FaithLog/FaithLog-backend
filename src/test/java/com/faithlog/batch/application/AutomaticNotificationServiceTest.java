package com.faithlog.batch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.billing.infrastructure.jpa.ChargeItemRepository;
import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.campus.infrastructure.jpa.CampusRepository;
import com.faithlog.devotion.domain.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.jpa.WeeklyDevotionRecordRepository;
import com.faithlog.notification.application.FcmTokenService;
import com.faithlog.notification.application.NotificationService;
import com.faithlog.notification.application.RegisterFcmTokenCommand;
import com.faithlog.notification.application.SendNotificationCommand;
import com.faithlog.notification.application.SendNotificationResult;
import com.faithlog.notification.application.port.NotificationDispatchPort;
import com.faithlog.notification.domain.DeviceType;
import com.faithlog.notification.domain.NotificationLog;
import com.faithlog.notification.domain.NotificationType;
import com.faithlog.notification.domain.SendStatus;
import com.faithlog.notification.infrastructure.jpa.NotificationLogRepository;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.domain.PollResponse;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
import com.faithlog.poll.infrastructure.jpa.PollResponseRepository;
import com.faithlog.support.NotificationConcurrencyTestConfig.InMemoryNotificationConcurrencyPort;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
class AutomaticNotificationServiceTest {

	@Autowired
	private AutomaticNotificationService automaticNotificationService;

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

	@Autowired
	private InMemoryNotificationConcurrencyPort notificationConcurrencyPort;

	@MockitoBean
	private NotificationDispatchPort notificationDispatchPort;

	@AfterEach
	void resetNotificationConcurrencyPort() {
		notificationConcurrencyPort.reset();
	}

	@Test
	void sendDevotionMissingReminders_targets_unsubmitted_members_from_monday_and_deduplicates_per_business_date() {
		Campus campus = saveCampus("자동경건캠");
		User minister = saveUser("auto-devotion-minister@example.com", UserRole.USER);
		User submitted = saveUser("auto-devotion-submitted@example.com", UserRole.USER);
		User missing = saveUser("auto-devotion-missing@example.com", UserRole.USER);
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), submitted.id());
		saveMember(campus.id(), missing.id());
		registerToken(missing, "auto-devotion-missing-token", "auto-devotion-missing-client");
		LocalDate targetWeekStartDate = LocalDate.of(2026, 6, 15);
		WeeklyDevotionRecord ministerRecord = WeeklyDevotionRecord.create(campus.id(), minister.id(), targetWeekStartDate);
		ministerRecord.submit(Instant.now());
		weeklyDevotionRecordRepository.saveAndFlush(ministerRecord);
		WeeklyDevotionRecord submittedRecord = WeeklyDevotionRecord.create(campus.id(), submitted.id(), targetWeekStartDate);
		submittedRecord.submit(Instant.now());
		weeklyDevotionRecordRepository.saveAndFlush(submittedRecord);

		int sundayQueued = automaticNotificationService.sendDevotionMissingReminders(
			seoul(2026, 6, 21, 11, 0).toInstant()
		);
		int mondayQueued = automaticNotificationService.sendDevotionMissingReminders(
			seoul(2026, 6, 22, 11, 0).toInstant()
		);
		int duplicateQueued = automaticNotificationService.sendDevotionMissingReminders(
			seoul(2026, 6, 22, 11, 1).toInstant()
		);

		assertThat(sundayQueued).isZero();
		assertThat(mondayQueued).isEqualTo(1);
		assertThat(duplicateQueued).isZero();
		assertThat(notificationLogRepository.findAll())
			.extracting(NotificationLog::userId, NotificationLog::notificationType, NotificationLog::targetWeekStartDate)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(
				missing.id(),
				NotificationType.DEVOTION_MISSING,
				targetWeekStartDate
			));
		assertThat(notificationLogRepository.findAll()).extracting(NotificationLog::sendStatus).containsOnly(SendStatus.PENDING);
	}

	@Test
	void automatic_devotion_dedup_does_not_block_manual_button_notification() {
		Campus campus = saveCampus("자동수동분리캠");
		User minister = saveUser("auto-manual-minister@example.com", UserRole.USER);
		User missing = saveUser("auto-manual-missing@example.com", UserRole.USER);
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), missing.id());
		registerToken(missing, "auto-manual-missing-token", "auto-manual-missing-client");
		LocalDate targetWeekStartDate = LocalDate.of(2026, 6, 15);
		WeeklyDevotionRecord ministerRecord = WeeklyDevotionRecord.create(campus.id(), minister.id(), targetWeekStartDate);
		ministerRecord.submit(Instant.now());
		weeklyDevotionRecordRepository.saveAndFlush(ministerRecord);

		int automaticQueued = automaticNotificationService.sendDevotionMissingReminders(
			seoul(2026, 6, 22, 11, 0).toInstant()
		);
		SendNotificationResult manualResult = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.DEVOTION_MISSING,
			null,
			targetWeekStartDate,
			null,
			"경건생활 제출 알림",
			"지난주 경건생활을 제출해 주세요."
		));

		assertThat(automaticQueued).isEqualTo(1);
		assertThat(manualResult.queuedCount()).isEqualTo(1);
		assertThat(notificationLogRepository.findAll()).hasSize(2);
	}

	@Test
	void sendDevotionMissingReminders_fails_closed_when_redis_is_unavailable() {
		Campus campus = saveCampus("자동경건락캠");
		User missing = saveUser("auto-devotion-lock-missing@example.com", UserRole.USER);
		saveMember(campus.id(), missing.id());
		registerToken(missing, "auto-devotion-lock-token", "auto-devotion-lock-client");
		notificationConcurrencyPort.fail();

		int queued = automaticNotificationService.sendDevotionMissingReminders(seoul(2026, 6, 22, 11, 0).toInstant());

		assertThat(queued).isZero();
		assertThat(notificationLogRepository.findAll()).isEmpty();
	}

	@Test
	void sendPollMissingReminders_scans_due_offsets_and_deduplicates_by_poll_and_offset() {
		Campus campus = saveCampus("자동투표캠");
		User responded = saveUser("auto-poll-responded@example.com", UserRole.USER);
		User missing = saveUser("auto-poll-missing@example.com", UserRole.USER);
		saveMember(campus.id(), responded.id());
		saveMember(campus.id(), missing.id());
		registerToken(missing, "auto-poll-missing-token", "auto-poll-missing-client");
		Instant endsAt = seoul(2026, 6, 22, 16, 0).toInstant();
		Poll poll = saveOpenPoll(campus.id(), "커피 투표", PollType.COFFEE, endsAt);
		pollResponseRepository.saveAndFlush(PollResponse.create(poll.id(), responded.id(), null));

		int fiveHours = automaticNotificationService.sendPollMissingReminders(endsAt.minusSeconds(5 * 3600));
		int fiveHoursDuplicate = automaticNotificationService.sendPollMissingReminders(endsAt.minusSeconds(5 * 3600).plusSeconds(30));
		int threeHours = automaticNotificationService.sendPollMissingReminders(endsAt.minusSeconds(3 * 3600));
		int twoHours = automaticNotificationService.sendPollMissingReminders(endsAt.minusSeconds(2 * 3600));
		int oneHour = automaticNotificationService.sendPollMissingReminders(endsAt.minusSeconds(3600));

		assertThat(List.of(fiveHours, fiveHoursDuplicate, threeHours, twoHours, oneHour))
			.containsExactly(1, 0, 1, 1, 1);
		assertThat(notificationLogRepository.findAll())
			.extracting(NotificationLog::userId, NotificationLog::notificationType, NotificationLog::targetId)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(missing.id(), NotificationType.COFFEE_POLL_MISSING, poll.id()),
				org.assertj.core.groups.Tuple.tuple(missing.id(), NotificationType.COFFEE_POLL_MISSING, poll.id()),
				org.assertj.core.groups.Tuple.tuple(missing.id(), NotificationType.COFFEE_POLL_MISSING, poll.id()),
				org.assertj.core.groups.Tuple.tuple(missing.id(), NotificationType.COFFEE_POLL_MISSING, poll.id())
			);
	}

	@Test
	void sendPollMissingReminders_includes_custom_poll_with_custom_notification_type() {
		Campus campus = saveCampus("자동커스텀투표캠");
		User responded = saveUser("auto-custom-poll-responded@example.com", UserRole.USER);
		User missing = saveUser("auto-custom-poll-missing@example.com", UserRole.USER);
		saveMember(campus.id(), responded.id());
		saveMember(campus.id(), missing.id());
		registerToken(missing, "auto-custom-poll-missing-token", "auto-custom-poll-missing-client");
		Instant endsAt = seoul(2026, 6, 22, 17, 0).toInstant();
		Poll poll = saveOpenPoll(campus.id(), "커스텀 투표", PollType.CUSTOM, endsAt);
		pollResponseRepository.saveAndFlush(PollResponse.create(poll.id(), responded.id(), null));

		int fiveHours = automaticNotificationService.sendPollMissingReminders(endsAt.minusSeconds(5 * 3600));
		int fiveHoursDuplicate = automaticNotificationService.sendPollMissingReminders(endsAt.minusSeconds(5 * 3600).plusSeconds(30));
		int threeHours = automaticNotificationService.sendPollMissingReminders(endsAt.minusSeconds(3 * 3600));
		int twoHours = automaticNotificationService.sendPollMissingReminders(endsAt.minusSeconds(2 * 3600));
		int oneHour = automaticNotificationService.sendPollMissingReminders(endsAt.minusSeconds(3600));

		assertThat(List.of(fiveHours, fiveHoursDuplicate, threeHours, twoHours, oneHour))
			.containsExactly(1, 0, 1, 1, 1);
		assertThat(notificationLogRepository.findAll())
			.extracting(NotificationLog::userId, NotificationLog::notificationType, NotificationLog::targetId)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(missing.id(), NotificationType.CUSTOM, poll.id()),
				org.assertj.core.groups.Tuple.tuple(missing.id(), NotificationType.CUSTOM, poll.id()),
				org.assertj.core.groups.Tuple.tuple(missing.id(), NotificationType.CUSTOM, poll.id()),
				org.assertj.core.groups.Tuple.tuple(missing.id(), NotificationType.CUSTOM, poll.id())
			);
	}

	@Test
	void sendPaymentUnpaidReminders_targets_unpaid_charges_and_deduplicates_by_business_date() {
		Campus campus = saveCampus("자동미납캠");
		User unpaid = saveUser("auto-payment-unpaid@example.com", UserRole.USER);
		User paid = saveUser("auto-payment-paid@example.com", UserRole.USER);
		saveMember(campus.id(), unpaid.id());
		saveMember(campus.id(), paid.id());
		registerToken(unpaid, "auto-payment-unpaid-token", "auto-payment-unpaid-client");
		chargeItemRepository.saveAndFlush(createCharge(campus.id(), unpaid.id(), 9001L));
		ChargeItem paidCharge = createCharge(campus.id(), paid.id(), 9002L);
		paidCharge.markPaid(Instant.now());
		chargeItemRepository.saveAndFlush(paidCharge);

		int first = automaticNotificationService.sendPaymentUnpaidReminders(seoul(2026, 6, 22, 12, 0).toInstant());
		int duplicate = automaticNotificationService.sendPaymentUnpaidReminders(seoul(2026, 6, 22, 12, 1).toInstant());

		assertThat(first).isEqualTo(1);
		assertThat(duplicate).isZero();
		assertThat(notificationLogRepository.findAll())
			.extracting(NotificationLog::userId, NotificationLog::notificationType)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(unpaid.id(), NotificationType.PAYMENT_UNPAID));
	}

	@Test
	void automatic_notifications_dispatch_queued_request_ids() {
		Campus campus = saveCampus("자동디스패치캠");
		User missing = saveUser("auto-dispatch-missing@example.com", UserRole.USER);
		saveMember(campus.id(), missing.id());
		registerToken(missing, "auto-dispatch-token", "auto-dispatch-client");

		automaticNotificationService.sendDevotionMissingReminders(seoul(2026, 6, 22, 11, 0).toInstant());

		NotificationLog log = notificationLogRepository.findAll().get(0);
		verify(notificationDispatchPort).dispatch(log.requestId());
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

	private Poll saveOpenPoll(Long campusId, String title, PollType pollType, Instant endsAt) {
		Poll poll = Poll.create(
			campusId,
			null,
			title,
			pollType,
			SelectionType.SINGLE,
			false,
			ChargeGenerationType.NONE,
			null,
			null,
			endsAt.minusSeconds(7 * 3600),
			endsAt,
			null
		);
		poll.open();
		return pollRepository.saveAndFlush(poll);
	}

	private ChargeItem createCharge(Long campusId, Long userId, Long sourceId) {
		return ChargeItem.create(
			campusId,
			userId,
			PaymentCategory.PENALTY,
			1L,
			"은행",
			"111",
			"회계",
			ChargeSourceType.DEVOTION_RECORD,
			sourceId,
			"미납 청구",
			"테스트",
			1000,
			null
		);
	}

	private User saveUser(String email, UserRole role) {
		User user = userRepository.save(User.create(email.substring(0, email.indexOf('@')), email, "{noop}password"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	private ZonedDateTime seoul(int year, int month, int day, int hour, int minute) {
		return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, PollAutomationService.SEOUL_ZONE);
	}
}
