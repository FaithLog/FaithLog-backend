package com.faithlog.notification.application;

import com.faithlog.notification.application.port.FcmSendCommand;
import com.faithlog.notification.application.port.FcmSendFailureType;
import com.faithlog.notification.application.port.FcmSendPort;
import com.faithlog.notification.domain.NotificationLog;
import com.faithlog.notification.domain.UserFcmToken;
import com.faithlog.notification.infrastructure.jpa.NotificationLogRepository;
import com.faithlog.notification.infrastructure.jpa.UserFcmTokenRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryWorker {

	private static final int MAX_TRANSIENT_RETRIES = 3;

	private final NotificationLogRepository notificationLogRepository;
	private final UserFcmTokenRepository userFcmTokenRepository;
	private final FcmSendPort fcmSendPort;
	private final NotificationRetryBackoff retryBackoff;

	public NotificationDeliveryWorker(
		NotificationLogRepository notificationLogRepository,
		UserFcmTokenRepository userFcmTokenRepository,
		FcmSendPort fcmSendPort,
		NotificationRetryBackoff retryBackoff
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.userFcmTokenRepository = userFcmTokenRepository;
		this.fcmSendPort = fcmSendPort;
		this.retryBackoff = retryBackoff;
	}

	@Transactional
	public void processRequest(UUID requestId) {
		List<NotificationLog> pendingLogs = notificationLogRepository
			.findByRequestIdAndSendStatusOrderByIdAsc(requestId, com.faithlog.notification.domain.SendStatus.PENDING);
		pendingLogs.forEach(this::processLog);
	}

	private void processLog(NotificationLog log) {
		List<UserFcmToken> tokens = userFcmTokenRepository.findActiveSendableTokens(log.userId());
		if (tokens.isEmpty()) {
			log.markSkipped("NO_ACTIVE_FCM_TOKEN");
			return;
		}

		boolean sent = false;
		String lastFailureReason = null;
		for (UserFcmToken token : tokens) {
			try {
				sendWithRetry(token, log);
				sent = true;
			} catch (FcmSendException exception) {
				lastFailureReason = exception.getMessage();
				token.recordFailure(lastFailureReason);
				if (exception.failureType() == FcmSendFailureType.PERMANENT) {
					token.deactivate();
				}
			} catch (RuntimeException exception) {
				lastFailureReason = exception.getMessage();
				token.recordFailure(lastFailureReason);
			}
		}

		if (sent) {
			log.markSent();
		} else {
			log.markFailed(lastFailureReason == null ? "FCM_SEND_FAILED" : lastFailureReason);
		}
	}

	private void sendWithRetry(UserFcmToken token, NotificationLog log) {
		int attempt = 0;
		while (true) {
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
}
