package com.faithlog.batch.service;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.devotion.domain.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.jpa.WeeklyDevotionRecordRepository;
import com.faithlog.notification.application.NotificationDeduplicationCommand;
import com.faithlog.notification.application.NotificationDeduplicationService;
import com.faithlog.notification.application.NotificationLockKey;
import com.faithlog.notification.application.NotificationLockLease;
import com.faithlog.notification.application.NotificationLockService;
import com.faithlog.notification.application.port.NotificationDispatchPort;
import com.faithlog.notification.domain.NotificationLog;
import com.faithlog.notification.domain.NotificationType;
import com.faithlog.notification.infrastructure.jpa.NotificationLogRepository;
import com.faithlog.notification.infrastructure.jpa.UserFcmTokenRepository;
import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.domain.PollResponse;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
import com.faithlog.poll.infrastructure.jpa.PollResponseRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AutomaticNotificationService {

	private static final ZoneId SEOUL_ZONE = PollAutomationService.SEOUL_ZONE;
	private static final List<Duration> POLL_REMINDER_OFFSETS = List.of(
		Duration.ofHours(5),
		Duration.ofHours(3),
		Duration.ofHours(2),
		Duration.ofHours(1)
	);
	private static final Duration POLL_REMINDER_SCAN_LOOKBACK = Duration.ofMinutes(1);

	private final CampusRepository campusRepository;
	private final CampusMemberRepository campusMemberRepository;
	private final WeeklyDevotionRecordRepository weeklyDevotionRecordRepository;
	private final PollRepository pollRepository;
	private final PollResponseRepository pollResponseRepository;
	private final ChargeItemRepository chargeItemRepository;
	private final UserFcmTokenRepository userFcmTokenRepository;
	private final NotificationLogRepository notificationLogRepository;
	private final NotificationDispatchPort notificationDispatchPort;
	private final NotificationDeduplicationService notificationDeduplicationService;
	private final NotificationLockService notificationLockService;
	private final TransactionTemplate transactionTemplate;

	public AutomaticNotificationService(
		CampusRepository campusRepository,
		CampusMemberRepository campusMemberRepository,
		WeeklyDevotionRecordRepository weeklyDevotionRecordRepository,
		PollRepository pollRepository,
		PollResponseRepository pollResponseRepository,
		ChargeItemRepository chargeItemRepository,
		UserFcmTokenRepository userFcmTokenRepository,
		NotificationLogRepository notificationLogRepository,
		NotificationDispatchPort notificationDispatchPort,
		NotificationDeduplicationService notificationDeduplicationService,
		NotificationLockService notificationLockService,
		PlatformTransactionManager transactionManager
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.weeklyDevotionRecordRepository = weeklyDevotionRecordRepository;
		this.pollRepository = pollRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.userFcmTokenRepository = userFcmTokenRepository;
		this.notificationLogRepository = notificationLogRepository;
		this.notificationDispatchPort = notificationDispatchPort;
		this.notificationDeduplicationService = notificationDeduplicationService;
		this.notificationLockService = notificationLockService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public int sendDevotionMissingReminders(Instant now) {
		LocalDate businessDate = LocalDate.ofInstant(now, SEOUL_ZONE);
		if (businessDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
			return 0;
		}
		LocalDate targetWeekStartDate = businessDate
			.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
			.minusWeeks(1);
		String scopeId = "week:" + targetWeekStartDate;
		return campusRepository.findByIsActiveTrueOrderByIdAsc()
			.stream()
			.mapToInt(campus -> runWithCampusLock(
				"devotion-missing",
				campus.id(),
				scopeId,
				() -> createDevotionMissingLogs(campus.id(), targetWeekStartDate, businessDate, scopeId)
			))
			.sum();
	}

	public int sendPollMissingReminders(Instant now) {
		Instant earliestEndsAt = now.plus(POLL_REMINDER_OFFSETS.get(POLL_REMINDER_OFFSETS.size() - 1))
			.minus(POLL_REMINDER_SCAN_LOOKBACK);
		Instant latestEndsAt = now.plus(POLL_REMINDER_OFFSETS.get(0)).plusSeconds(1);
		return pollRepository.findByStatusAndEndsAtBetweenOrderByIdAsc(PollStatus.OPEN, earliestEndsAt, latestEndsAt)
			.stream()
			.mapToInt(poll -> sendDuePollReminders(poll, now))
			.sum();
	}

	public int sendPaymentUnpaidReminders(Instant now) {
		LocalDate businessDate = LocalDate.ofInstant(now, SEOUL_ZONE);
		String scopeId = "payment:unpaid";
		return campusRepository.findByIsActiveTrueOrderByIdAsc()
			.stream()
			.mapToInt(campus -> runWithCampusLock(
				"payment-unpaid",
				campus.id(),
				businessDate.toString(),
				() -> createPaymentUnpaidLogs(campus.id(), businessDate, scopeId)
			))
			.sum();
	}

	private int sendDuePollReminders(Poll poll, Instant now) {
		return POLL_REMINDER_OFFSETS.stream()
			.filter(offset -> isReminderDue(poll.endsAt().minus(offset), now))
			.mapToInt(offset -> {
				NotificationType notificationType = pollNotificationType(poll.pollType()).orElse(null);
				if (notificationType == null) {
					return 0;
				}
				String offsetScope = offset.toHours() + "h";
				String scopeId = "poll:" + poll.id() + ":offset:" + offsetScope;
				LocalDate businessDate = LocalDate.ofInstant(poll.endsAt().minus(offset), SEOUL_ZONE);
				return runWithCampusLock(
					"poll-missing",
					poll.campusId(),
					scopeId,
					() -> createPollMissingLogs(poll, notificationType, businessDate, scopeId)
				);
			})
			.sum();
	}

	private boolean isReminderDue(Instant reminderAt, Instant now) {
		return !reminderAt.isBefore(now.minus(POLL_REMINDER_SCAN_LOOKBACK)) && reminderAt.isBefore(now.plusSeconds(1));
	}

	private int createDevotionMissingLogs(
		Long campusId,
		LocalDate targetWeekStartDate,
		LocalDate businessDate,
		String scopeId
	) {
		return createAutomaticLogs(
			campusId,
			NotificationType.DEVOTION_MISSING,
			targetWeekStartDate,
			null,
			devotionMissingTargets(campusId, targetWeekStartDate),
			businessDate,
			scopeId,
			"경건생활 제출 알림",
			"지난주 경건생활을 아직 제출하지 않았어요. 제출 상태를 확인해 주세요."
		);
	}

	private int createPollMissingLogs(
		Poll poll,
		NotificationType notificationType,
		LocalDate businessDate,
		String scopeId
	) {
		return createAutomaticLogs(
			poll.campusId(),
			notificationType,
			null,
			poll.id(),
			pollMissingTargets(poll),
			businessDate,
			scopeId,
			"투표 참여 알림",
			"마감 전 아직 응답하지 않은 투표가 있어요."
		);
	}

	private int createPaymentUnpaidLogs(Long campusId, LocalDate businessDate, String scopeId) {
		return createAutomaticLogs(
			campusId,
			NotificationType.PAYMENT_UNPAID,
			null,
			null,
			paymentUnpaidTargets(campusId),
			businessDate,
			scopeId,
			"미납 알림",
			"아직 납부하지 않은 청구가 있어요."
		);
	}

	private int createAutomaticLogs(
		Long campusId,
		NotificationType notificationType,
		LocalDate targetWeekStartDate,
		Long targetId,
		List<Long> targetUserIds,
		LocalDate businessDate,
		String scopeId,
		String title,
		String body
	) {
		NotificationBatchResult result = transactionTemplate.execute(status -> {
			UUID requestId = UUID.randomUUID();
			int queuedCount = 0;
			int createdCount = 0;
			for (Long targetUserId : targetUserIds) {
				boolean reserved = notificationDeduplicationService.reserveDailyAutomaticNotification(
					new NotificationDeduplicationCommand(
						notificationType,
						campusId,
						scopeId,
						targetUserId,
						businessDate
					)
				);
				if (!reserved) {
					continue;
				}
				if (userFcmTokenRepository.findActiveSendableTokens(targetUserId).isEmpty()) {
					notificationLogRepository.save(NotificationLog.skipped(
						requestId,
						targetUserId,
						campusId,
						notificationType,
						targetWeekStartDate,
						targetId,
						title,
						body,
						"NO_ACTIVE_FCM_TOKEN"
					));
					createdCount++;
					continue;
				}
				notificationLogRepository.save(NotificationLog.pending(
					requestId,
					targetUserId,
					campusId,
					notificationType,
					targetWeekStartDate,
					targetId,
					title,
					body
				));
				queuedCount++;
				createdCount++;
			}
			return new NotificationBatchResult(requestId, queuedCount, createdCount);
		});
		if (result == null || result.createdCount() == 0) {
			return 0;
		}
		if (result.queuedCount() > 0) {
			notificationDispatchPort.dispatch(result.requestId());
		}
		return result.createdCount();
	}

	private List<Long> devotionMissingTargets(Long campusId, LocalDate targetWeekStartDate) {
		List<CampusMember> activeMembers = campusMemberRepository
			.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE);
		Map<Long, WeeklyDevotionRecord> recordsByUserId = weeklyDevotionRecordRepository
			.findByCampusIdAndWeekStartDate(campusId, targetWeekStartDate)
			.stream()
			.collect(Collectors.toMap(WeeklyDevotionRecord::userId, Function.identity()));
		return activeMembers.stream()
			.filter(member -> {
				WeeklyDevotionRecord record = recordsByUserId.get(member.userId());
				return record == null || record.submittedAt() == null;
			})
			.map(CampusMember::userId)
			.toList();
	}

	private List<Long> pollMissingTargets(Poll poll) {
		Set<Long> respondedUserIds = pollResponseRepository.findByPollIdOrderByIdAsc(poll.id())
			.stream()
			.map(PollResponse::userId)
			.collect(Collectors.toSet());
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(poll.campusId(), CampusMemberStatus.ACTIVE)
			.stream()
			.filter(member -> !respondedUserIds.contains(member.userId()))
			.map(CampusMember::userId)
			.toList();
	}

	private List<Long> paymentUnpaidTargets(Long campusId) {
		Set<Long> activeUserIds = campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE)
			.stream()
			.map(CampusMember::userId)
			.collect(Collectors.toSet());
		LinkedHashSet<Long> unpaidUserIds = chargeItemRepository.findByCampusIdAndStatus(campusId, ChargeStatus.UNPAID)
			.stream()
			.map(ChargeItem::userId)
			.filter(activeUserIds::contains)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return List.copyOf(unpaidUserIds);
	}

	private Optional<NotificationType> pollNotificationType(PollType pollType) {
		return switch (pollType) {
			case WED_SERVICE -> Optional.of(NotificationType.WED_POLL_MISSING);
			case SATURDAY_LEADER -> Optional.of(NotificationType.SATURDAY_POLL_MISSING);
			case COFFEE -> Optional.of(NotificationType.COFFEE_POLL_MISSING);
			case CUSTOM -> Optional.of(NotificationType.CUSTOM);
		};
	}

	private int runWithCampusLock(String jobName, Long campusId, String scopeId, LockedNotificationJob job) {
		Optional<NotificationLockLease> lease = notificationLockService.acquireScheduledLock(
			new NotificationLockKey(jobName, campusId, scopeId)
		);
		if (lease.isEmpty()) {
			return 0;
		}
		try {
			return job.run();
		} finally {
			notificationLockService.release(lease.get());
		}
	}

	@FunctionalInterface
	private interface LockedNotificationJob {
		int run();
	}

	private record NotificationBatchResult(UUID requestId, int queuedCount, int createdCount) {
	}
}
