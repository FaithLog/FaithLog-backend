package com.faithlog.batch.service;

import com.faithlog.batch.service.result.DataRetentionCleanupResult;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockService;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.poll.domain.entity.PollImage;
import com.faithlog.poll.infrastructure.repository.PollCommentRepository;
import com.faithlog.poll.infrastructure.repository.PollImageRepository;
import com.faithlog.poll.infrastructure.repository.PollNotificationOutboxRepository;
import com.faithlog.poll.infrastructure.repository.PollOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseOptionRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerSubmissionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DataRetentionCleanupService {

	static final ZoneId SEOUL_ZONE = BatchTimeZone.SEOUL_ZONE;

	private static final Duration RETENTION_LOCK_TTL = Duration.ofMinutes(30);
	private static final Duration NOTIFICATION_LOG_RETENTION = Duration.ofDays(14);
	private static final Duration POLL_RETENTION = Duration.ofDays(30);
	private static final Duration SOFT_DELETED_COMMENT_RETENTION = Duration.ofDays(30);
	private static final int MEDIA_LOCK_BATCH_SIZE = 100;
	private static final List<ChargeStatus> TERMINAL_CHARGE_STATUSES = List.of(
		ChargeStatus.PAID,
		ChargeStatus.WAIVED,
		ChargeStatus.CANCELED
	);

	private final NotificationLogRepository notificationLogRepository;
	private final PollRepository pollRepository;
	private final PollResponseOptionRepository pollResponseOptionRepository;
	private final PollResponseRepository pollResponseRepository;
	private final PollCommentRepository pollCommentRepository;
	private final PollOptionRepository pollOptionRepository;
	private final PollImageRepository pollImageRepository;
	private final PollNotificationOutboxRepository pollNotificationOutboxRepository;
	private final MediaAssetRepositoryPort mediaAssetRepository;
	private final PrayerSubmissionRepository prayerSubmissionRepository;
	private final DevotionDailyCheckRepository dailyCheckRepository;
	private final WeeklyDevotionRecordRepository weeklyRecordRepository;
	private final ChargeItemRepository chargeItemRepository;
	private final NotificationLockService notificationLockService;
	private final TransactionTemplate transactionTemplate;

	public DataRetentionCleanupService(
		NotificationLogRepository notificationLogRepository,
		PollRepository pollRepository,
		PollResponseOptionRepository pollResponseOptionRepository,
		PollResponseRepository pollResponseRepository,
		PollCommentRepository pollCommentRepository,
		PollOptionRepository pollOptionRepository,
		PollImageRepository pollImageRepository,
		PollNotificationOutboxRepository pollNotificationOutboxRepository,
		MediaAssetRepositoryPort mediaAssetRepository,
		PrayerSubmissionRepository prayerSubmissionRepository,
		DevotionDailyCheckRepository dailyCheckRepository,
		WeeklyDevotionRecordRepository weeklyRecordRepository,
		ChargeItemRepository chargeItemRepository,
		NotificationLockService notificationLockService,
		PlatformTransactionManager transactionManager
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.pollRepository = pollRepository;
		this.pollResponseOptionRepository = pollResponseOptionRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.pollCommentRepository = pollCommentRepository;
		this.pollOptionRepository = pollOptionRepository;
		this.pollImageRepository = pollImageRepository;
		this.pollNotificationOutboxRepository = pollNotificationOutboxRepository;
		this.mediaAssetRepository = mediaAssetRepository;
		this.prayerSubmissionRepository = prayerSubmissionRepository;
		this.dailyCheckRepository = dailyCheckRepository;
		this.weeklyRecordRepository = weeklyRecordRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.notificationLockService = notificationLockService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public DataRetentionCleanupResult cleanupDueData(Instant now) {
		return cleanupDaily(now).plus(cleanupAnnualIfDue(now));
	}

	public DataRetentionCleanupResult cleanupDaily(Instant now) {
		LocalDate businessDate = LocalDate.ofInstant(now, SEOUL_ZONE);
		NotificationLockKey lockKey = NotificationLockKey.of("notification:lock:data-retention:daily:" + businessDate);
		return notificationLockService.acquireScheduledLock(lockKey, RETENTION_LOCK_TTL)
			.map(lease -> {
				try {
					return transactionTemplate.execute(status -> cleanupDailyInTransaction(now));
				} finally {
					notificationLockService.release(lease);
				}
			})
			.orElseGet(DataRetentionCleanupResult::empty);
	}

	public DataRetentionCleanupResult cleanupAnnualIfDue(Instant now) {
		LocalDate businessDate = LocalDate.ofInstant(now, SEOUL_ZONE);
		if (businessDate.getMonth() != Month.FEBRUARY || businessDate.getDayOfMonth() != 1) {
			return DataRetentionCleanupResult.empty();
		}
		NotificationLockKey lockKey = NotificationLockKey.of(
			"notification:lock:data-retention:annual:" + businessDate.getYear()
		);
		return notificationLockService.acquireScheduledLock(lockKey, RETENTION_LOCK_TTL)
			.map(lease -> {
				try {
					return transactionTemplate.execute(status -> cleanupAnnualInTransaction(businessDate));
				} finally {
					notificationLockService.release(lease);
				}
			})
			.orElseGet(DataRetentionCleanupResult::empty);
	}

	private DataRetentionCleanupResult cleanupDailyInTransaction(Instant now) {
		Instant notificationLogCutoff = now.minus(NOTIFICATION_LOG_RETENTION);
		Instant pollCutoff = now.minus(POLL_RETENTION);
		Instant softDeletedCommentCutoff = now.minus(SOFT_DELETED_COMMENT_RETENTION);
		Instant prayerSubmissionCutoff = now.atZone(SEOUL_ZONE).minusYears(1).toInstant();

		int notificationLogsDeleted = notificationLogRepository.deleteByCreatedAtBefore(notificationLogCutoff);
		List<Long> expiredPollIds = pollRepository.findIdsByEndsAtBefore(pollCutoff);
		int pollResponseOptionsDeleted = 0;
		int pollResponsesDeleted = 0;
		int pollCommentsDeleted = 0;
		int pollOptionsDeleted = 0;
		int pollsDeleted = 0;
		if (!expiredPollIds.isEmpty()) {
			orphanExpiredPollMedia(expiredPollIds, now);
			pollImageRepository.deleteByPollIdIn(expiredPollIds);
			pollNotificationOutboxRepository.deleteByPollIdIn(expiredPollIds);
			pollResponseOptionsDeleted = pollResponseOptionRepository.deleteByPollIdIn(expiredPollIds);
			pollResponsesDeleted = pollResponseRepository.deleteByPollIdIn(expiredPollIds);
			pollCommentsDeleted = pollCommentRepository.deleteByPollIdIn(expiredPollIds);
			pollOptionsDeleted = pollOptionRepository.deleteByPollIdIn(expiredPollIds);
			pollsDeleted = pollRepository.deleteByIdIn(expiredPollIds);
		}
		int softDeletedCommentsDeleted = pollCommentRepository.deleteSoftDeletedBefore(softDeletedCommentCutoff);
		int prayerSubmissionsDeleted = prayerSubmissionRepository.deleteByCreatedAtBefore(prayerSubmissionCutoff);

		return new DataRetentionCleanupResult(
			notificationLogsDeleted,
			pollResponseOptionsDeleted,
			pollResponsesDeleted,
			pollCommentsDeleted,
			pollOptionsDeleted,
			pollsDeleted,
			softDeletedCommentsDeleted,
			prayerSubmissionsDeleted,
			0,
			0,
			0
		);
	}

	private void orphanExpiredPollMedia(List<Long> expiredPollIds, Instant now) {
		List<PollImage> attachments = pollImageRepository
			.findByPollIdInOrderByPollIdAscDisplayOrderAscIdAsc(expiredPollIds);
		if (attachments.isEmpty()) {
			return;
		}
		List<Long> sortedAssetIds = attachments.stream()
			.map(PollImage::mediaAssetId)
			.distinct()
			.sorted()
			.toList();
		Map<Long, MediaAsset> assetsById = new LinkedHashMap<>();
		for (int start = 0; start < sortedAssetIds.size(); start += MEDIA_LOCK_BATCH_SIZE) {
			List<Long> batch = sortedAssetIds.subList(
				start,
				Math.min(start + MEDIA_LOCK_BATCH_SIZE, sortedAssetIds.size()));
			mediaAssetRepository.findByIdInForUpdate(batch)
				.forEach(asset -> assetsById.put(asset.id(), asset));
		}
		if (assetsById.size() != sortedAssetIds.size()) {
			throw new IllegalStateException("expired poll media asset is missing");
		}
		for (PollImage attachment : attachments) {
			MediaAsset asset = assetsById.get(attachment.mediaAssetId());
			if (asset == null
				|| !asset.campusId().equals(attachment.campusId())
				|| asset.status() != MediaAssetStatus.READY) {
				throw new IllegalStateException("expired poll media asset is not ready");
			}
		}
		assetsById.values().forEach(asset -> asset.markOrphaned(now));
	}

	private DataRetentionCleanupResult cleanupAnnualInTransaction(LocalDate businessDate) {
		int previousYear = businessDate.getYear() - 1;
		LocalDate startDate = LocalDate.of(previousYear, 1, 1);
		LocalDate endDate = LocalDate.of(previousYear, 12, 31);
		Instant startInstant = startDate.atStartOfDay(SEOUL_ZONE).toInstant();
		Instant endExclusiveInstant = startDate.plusYears(1).atStartOfDay(SEOUL_ZONE).toInstant();

		int dailyChecksDeleted = dailyCheckRepository.deleteByRecordDateBetween(startDate, endDate);
		int weeklyRecordsDeleted = weeklyRecordRepository.deleteByWeekStartDateBetween(startDate, endDate);
		int chargeItemsDeleted = chargeItemRepository.deleteByStatusInAndCreatedAtBetween(
			TERMINAL_CHARGE_STATUSES,
			startInstant,
			endExclusiveInstant
		);

		return new DataRetentionCleanupResult(
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			dailyChecksDeleted,
			weeklyRecordsDeleted,
			chargeItemsDeleted
		);
	}
}
