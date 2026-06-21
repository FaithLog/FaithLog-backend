package com.faithlog.batch.application;

import com.faithlog.notification.application.NotificationDeliveryWorker;
import com.faithlog.notification.application.NotificationLockKey;
import com.faithlog.notification.application.NotificationLockLease;
import com.faithlog.notification.application.NotificationLockService;
import com.faithlog.notification.domain.NotificationLog;
import com.faithlog.notification.domain.SendStatus;
import com.faithlog.notification.infrastructure.jpa.NotificationLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PendingNotificationRecoveryService {

	private static final Logger log = LoggerFactory.getLogger(PendingNotificationRecoveryService.class);
	private static final Duration STALE_PENDING_AGE = Duration.ofMinutes(10);
	private static final String STILL_PENDING_FAILURE_REASON = "PENDING_REPROCESS_FAILED";

	private final NotificationLogRepository notificationLogRepository;
	private final NotificationDeliveryWorker notificationDeliveryWorker;
	private final NotificationLockService notificationLockService;
	private final TransactionTemplate transactionTemplate;

	public PendingNotificationRecoveryService(
		NotificationLogRepository notificationLogRepository,
		NotificationDeliveryWorker notificationDeliveryWorker,
		NotificationLockService notificationLockService,
		PlatformTransactionManager transactionManager
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.notificationDeliveryWorker = notificationDeliveryWorker;
		this.notificationLockService = notificationLockService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public int reprocessStalePendingLogs(Instant now) {
		Instant staleThreshold = now.minus(STALE_PENDING_AGE);
		Map<UUID, Long> campusIdsByRequestId = transactionTemplate.execute(status -> notificationLogRepository
			.findBySendStatusAndCreatedAtLessThanEqualOrderByIdAsc(SendStatus.PENDING, staleThreshold)
			.stream()
			.collect(Collectors.toMap(
				NotificationLog::requestId,
				NotificationLog::campusId,
				(left, right) -> left,
				LinkedHashMap::new
			)));
		if (campusIdsByRequestId == null || campusIdsByRequestId.isEmpty()) {
			return 0;
		}
		int handledCount = 0;
		for (Map.Entry<UUID, Long> entry : campusIdsByRequestId.entrySet()) {
			handledCount += reprocessRequest(entry.getKey(), entry.getValue());
		}
		return handledCount;
	}

	private int reprocessRequest(UUID requestId, Long campusId) {
		Optional<NotificationLockLease> lease = notificationLockService.acquireScheduledLock(
			new NotificationLockKey("pending-notification-reprocess", campusId, "request:" + requestId)
		);
		if (lease.isEmpty()) {
			return 0;
		}
		try {
			try {
				notificationDeliveryWorker.processRequest(requestId);
			} catch (RuntimeException exception) {
				log.warn("Pending notification reprocess worker failed: requestId={}", requestId, exception);
			}
			markStillPendingLogsFailed(requestId);
			return 1;
		} finally {
			notificationLockService.release(lease.get());
		}
	}

	private void markStillPendingLogsFailed(UUID requestId) {
		transactionTemplate.executeWithoutResult(status -> {
			List<NotificationLog> pendingLogs = notificationLogRepository
				.findByRequestIdAndSendStatusOrderByIdAsc(requestId, SendStatus.PENDING);
			pendingLogs.forEach(log -> log.markFailed(STILL_PENDING_FAILURE_REASON));
		});
	}
}
