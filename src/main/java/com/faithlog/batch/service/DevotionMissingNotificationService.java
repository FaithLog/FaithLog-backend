package com.faithlog.batch.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockLease;
import com.faithlog.notification.service.NotificationLockService;
import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.notification.service.command.AutomaticNotificationRequestCommand;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DevotionMissingNotificationService {

	private final CampusRepository campusRepository;
	private final CampusMemberRepository campusMemberRepository;
	private final WeeklyDevotionRecordRepository weeklyDevotionRecordRepository;
	private final NotificationRequestCommandService notificationRequestCommandService;
	private final NotificationLockService notificationLockService;

	public DevotionMissingNotificationService(
		CampusRepository campusRepository,
		CampusMemberRepository campusMemberRepository,
		WeeklyDevotionRecordRepository weeklyDevotionRecordRepository,
		NotificationRequestCommandService notificationRequestCommandService,
		NotificationLockService notificationLockService
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.weeklyDevotionRecordRepository = weeklyDevotionRecordRepository;
		this.notificationRequestCommandService = notificationRequestCommandService;
		this.notificationLockService = notificationLockService;
	}

	public int sendDevotionMissingReminders(Instant now) {
		LocalDate businessDate = LocalDate.ofInstant(now, BatchTimeZone.SEOUL_ZONE);
		if (businessDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
			return 0;
		}
		LocalDate targetWeekStartDate = businessDate
			.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
			.minusWeeks(1);
		String scopeId = "week:" + targetWeekStartDate;
		return campusRepository.findByIsActiveTrueOrderByIdAsc()
			.stream()
			.mapToInt(campus -> runWithCampusLock(
				campus.id(),
				scopeId,
				() -> notificationRequestCommandService.requestAutomaticNotification(
					new AutomaticNotificationRequestCommand(
						campus.id(),
						NotificationType.DEVOTION_MISSING,
						targetWeekStartDate,
						null,
						devotionMissingTargets(campus.id(), targetWeekStartDate),
						businessDate,
						scopeId,
						"경건생활 제출 알림",
						"지난주 경건생활을 아직 제출하지 않았어요. 제출 상태를 확인해 주세요."
					)
				)
			))
			.sum();
	}

	private java.util.List<Long> devotionMissingTargets(Long campusId, LocalDate targetWeekStartDate) {
		java.util.List<CampusMember> activeMembers = campusMemberRepository
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

	private int runWithCampusLock(Long campusId, String scopeId, LockedNotificationJob job) {
		Optional<NotificationLockLease> lease = notificationLockService.acquireScheduledLock(
			new NotificationLockKey("devotion-missing", campusId, scopeId)
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
}
