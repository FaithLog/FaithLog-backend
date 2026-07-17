package com.faithlog.notification.service;

import com.faithlog.notification.service.port.FcmSendCommand;
import com.faithlog.notification.service.port.FcmSendFailureType;
import com.faithlog.notification.service.port.FcmSendPort;
import com.faithlog.notification.domain.entity.NotificationLog;
import com.faithlog.notification.domain.type.SendStatus;
import com.faithlog.notification.domain.entity.UserFcmToken;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationDeliveryWorker {

	private static final int MAX_TRANSIENT_RETRIES = 3;

	private final NotificationLogRepository notificationLogRepository;
	private final UserFcmTokenRepository userFcmTokenRepository;
	private final FcmSendPort fcmSendPort;
	private final NotificationRetryBackoff retryBackoff;
	private final TransactionTemplate transactionTemplate;
	private final NotificationLockService notificationLockService;

	public NotificationDeliveryWorker(
		NotificationLogRepository notificationLogRepository,
		UserFcmTokenRepository userFcmTokenRepository,
		FcmSendPort fcmSendPort,
		NotificationRetryBackoff retryBackoff,
		PlatformTransactionManager transactionManager,
		NotificationLockService notificationLockService
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.userFcmTokenRepository = userFcmTokenRepository;
		this.fcmSendPort = fcmSendPort;
		this.retryBackoff = retryBackoff;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.notificationLockService = notificationLockService;
	}

	public void processRequest(UUID requestId) {
		tryProcessRequest(requestId);
	}

	public boolean tryProcessRequest(UUID requestId) {
		Long campusId = transactionTemplate.execute(status -> notificationLogRepository
			.findCampusIdByRequestId(requestId)
			.orElse(null));
		if (campusId == null) {
			return true;
		}
		Optional<NotificationLockLease> acquired = notificationLockService.acquireScheduledLock(
			NotificationLockKey.dispatch(campusId, requestId));
		if (acquired.isEmpty()) {
			return false;
		}
		NotificationLockLease lease = acquired.orElseThrow();
		try {
			List<PendingNotificationLog> pendingLogs = transactionTemplate.execute(status -> notificationLogRepository
				.findByRequestIdAndSendStatusOrderByIdAsc(requestId, SendStatus.PENDING)
				.stream()
				.map(PendingNotificationLog::from)
				.toList());
			if (pendingLogs == null || pendingLogs.isEmpty()) {
				return true;
			}
			Set<Long> pendingUserIds = pendingLogs.stream()
				.map(PendingNotificationLog::userId)
				.collect(Collectors.toSet());
			Map<Long, List<PendingFcmToken>> tokensByUserId = transactionTemplate.execute(status ->
				userFcmTokenRepository.findActiveSendableTokensByUserIdIn(pendingUserIds)
					.stream()
					.collect(Collectors.groupingBy(
						UserFcmToken::userId,
						Collectors.mapping(PendingFcmToken::from, Collectors.toList())
					))
			);
			for (PendingNotificationLog log : pendingLogs) {
				ensureLease(lease);
				processLog(
					log,
					tokensByUserId == null ? List.of() : tokensByUserId.getOrDefault(log.userId(), List.of()),
					lease
				);
			}
			return true;
		} catch (NotificationLockLostException exception) {
			return false;
		} finally {
			notificationLockService.release(lease);
		}
	}

	private void processLog(
		PendingNotificationLog log,
		List<PendingFcmToken> tokens,
		NotificationLockLease lease
	) {
		if (tokens.isEmpty()) {
			markLogSkipped(log.id(), "NO_ACTIVE_FCM_TOKEN");
			return;
		}

		boolean sent = false;
		String lastFailureReason = null;
		for (Iterator<PendingFcmToken> iterator = tokens.iterator(); iterator.hasNext();) {
			PendingFcmToken token = iterator.next();
			try {
				sendWithRetry(token, log, lease);
				sent = true;
			} catch (NotificationLockLostException exception) {
				throw exception;
			} catch (FcmSendException exception) {
				lastFailureReason = exception.getMessage();
				boolean permanent = exception.failureType() == FcmSendFailureType.PERMANENT;
				recordTokenFailure(token.id(), lastFailureReason, permanent);
				if (permanent) {
					iterator.remove();
				}
			} catch (RuntimeException exception) {
				lastFailureReason = exception.getMessage();
				recordTokenFailure(token.id(), lastFailureReason, false);
			}
		}

		if (sent) {
			markLogSent(log.id());
		} else {
			markLogFailed(log.id(), lastFailureReason == null ? "FCM_SEND_FAILED" : lastFailureReason);
		}
	}

	private void sendWithRetry(
		PendingFcmToken token,
		PendingNotificationLog log,
		NotificationLockLease lease
	) {
		int attempt = 0;
		while (true) {
			ensureLease(lease);
			try {
				fcmSendPort.send(new FcmSendCommand(token.token(), log.title(), log.body()));
				return;
			} catch (FcmSendException exception) {
				if (exception.failureType() == FcmSendFailureType.PERMANENT || attempt >= MAX_TRANSIENT_RETRIES) {
					throw exception;
				}
				attempt++;
				retryBackoff.sleepBeforeRetry(attempt);
			} catch (RuntimeException exception) {
				if (attempt >= MAX_TRANSIENT_RETRIES) {
					throw new FcmSendException(FcmSendFailureType.TRANSIENT, exception.getMessage());
				}
				attempt++;
				retryBackoff.sleepBeforeRetry(attempt);
			}
		}
	}

	private void ensureLease(NotificationLockLease lease) {
		if (!notificationLockService.renewScheduledLock(lease)) {
			throw new NotificationLockLostException();
		}
	}

	private static class NotificationLockLostException extends RuntimeException {
	}

	private void markLogSkipped(Long logId, String failureReason) {
		transactionTemplate.executeWithoutResult(status -> {
			NotificationLog log = notificationLogRepository.findById(logId).orElseThrow();
			log.markSkipped(failureReason);
		});
	}

	private void markLogSent(Long logId) {
		transactionTemplate.executeWithoutResult(status -> {
			NotificationLog log = notificationLogRepository.findById(logId).orElseThrow();
			log.markSent();
		});
	}

	private void markLogFailed(Long logId, String failureReason) {
		transactionTemplate.executeWithoutResult(status -> {
			NotificationLog log = notificationLogRepository.findById(logId).orElseThrow();
			log.markFailed(failureReason);
		});
	}

	private void recordTokenFailure(Long tokenId, String failureReason, boolean deactivate) {
		transactionTemplate.executeWithoutResult(status -> {
			UserFcmToken token = userFcmTokenRepository.findById(tokenId).orElseThrow();
			token.recordFailure(failureReason);
			if (deactivate) {
				token.deactivate();
			}
		});
	}
}
