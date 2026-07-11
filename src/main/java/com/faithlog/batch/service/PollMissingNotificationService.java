package com.faithlog.batch.service;

import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockLease;
import com.faithlog.notification.service.NotificationLockService;
import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.notification.service.command.AutomaticNotificationRequestCommand;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PollMissingNotificationService {

	private static final List<Duration> POLL_REMINDER_OFFSETS = List.of(
		Duration.ofHours(5),
		Duration.ofHours(3),
		Duration.ofHours(2),
		Duration.ofHours(1)
	);
	private static final Duration POLL_REMINDER_SCAN_LOOKBACK = Duration.ofMinutes(1);

	private final CampusMemberRepository campusMemberRepository;
	private final PollRepository pollRepository;
	private final PollResponseRepository pollResponseRepository;
	private final NotificationRequestCommandService notificationRequestCommandService;
	private final NotificationLockService notificationLockService;

	public PollMissingNotificationService(
		CampusMemberRepository campusMemberRepository,
		PollRepository pollRepository,
		PollResponseRepository pollResponseRepository,
		NotificationRequestCommandService notificationRequestCommandService,
		NotificationLockService notificationLockService
	) {
		this.campusMemberRepository = campusMemberRepository;
		this.pollRepository = pollRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.notificationRequestCommandService = notificationRequestCommandService;
		this.notificationLockService = notificationLockService;
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
				LocalDate businessDate = LocalDate.ofInstant(
					poll.endsAt().minus(offset),
					BatchTimeZone.SEOUL_ZONE
				);
				return runWithCampusLock(poll, notificationType, businessDate, scopeId);
			})
			.sum();
	}

	private boolean isReminderDue(Instant reminderAt, Instant now) {
		return !reminderAt.isBefore(now.minus(POLL_REMINDER_SCAN_LOOKBACK)) && reminderAt.isBefore(now.plusSeconds(1));
	}

	private int runWithCampusLock(
		Poll poll,
		NotificationType notificationType,
		LocalDate businessDate,
		String scopeId
	) {
		Optional<NotificationLockLease> lease = notificationLockService.acquireScheduledLock(
			new NotificationLockKey("poll-missing", poll.campusId(), scopeId)
		);
		if (lease.isEmpty()) {
			return 0;
		}
		try {
			return notificationRequestCommandService.requestAutomaticNotification(
				new AutomaticNotificationRequestCommand(
					poll.campusId(),
					notificationType,
					null,
					poll.id(),
					pollMissingTargets(poll),
					businessDate,
					scopeId,
					"투표 참여 알림",
					"마감 전 아직 응답하지 않은 투표가 있어요."
				)
			);
		} finally {
			notificationLockService.release(lease.get());
		}
	}

	private List<Long> pollMissingTargets(Poll poll) {
		Set<Long> respondedUserIds = pollResponseRepository.findByPollIdOrderByIdAsc(poll.id())
			.stream()
			.map(PollResponse::userId)
			.collect(Collectors.toSet());
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(poll.campusId(), CampusMemberStatus.ACTIVE)
			.stream()
			.filter(member -> !respondedUserIds.contains(member.userId()))
			.map(member -> member.userId())
			.toList();
	}

	private Optional<NotificationType> pollNotificationType(PollType pollType) {
		return switch (pollType) {
			case WED_SERVICE -> Optional.of(NotificationType.WED_POLL_MISSING);
			case SATURDAY_LEADER -> Optional.of(NotificationType.SATURDAY_POLL_MISSING);
			case COFFEE -> Optional.of(NotificationType.COFFEE_POLL_MISSING);
			case CUSTOM -> Optional.of(NotificationType.CUSTOM);
		};
	}
}
