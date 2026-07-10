package com.faithlog.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.faithlog.notification.application.NotificationDeliveryWorker;
import com.faithlog.notification.domain.NotificationLog;
import com.faithlog.notification.domain.NotificationType;
import com.faithlog.notification.domain.SendStatus;
import com.faithlog.notification.infrastructure.jpa.NotificationLogRepository;
import com.faithlog.support.NotificationConcurrencyTestConfig.InMemoryNotificationConcurrencyPort;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PendingNotificationRecoveryServiceTest {

	@Autowired
	private PendingNotificationRecoveryService pendingNotificationRecoveryService;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private InMemoryNotificationConcurrencyPort notificationConcurrencyPort;

	@MockitoBean
	private NotificationDeliveryWorker notificationDeliveryWorker;

	@AfterEach
	void resetNotificationConcurrencyPort() {
		notificationConcurrencyPort.reset();
	}

	@Test
	void reprocessPendingLogs_reprocesses_only_logs_older_than_ten_minutes_once() {
		UUID staleRequestId = UUID.randomUUID();
		UUID recentRequestId = UUID.randomUUID();
		savePendingLog(staleRequestId, 1L, 1L, Instant.parse("2026-06-22T01:49:59Z"));
		savePendingLog(recentRequestId, 2L, 1L, Instant.parse("2026-06-22T01:50:01Z"));
		doNothing().when(notificationDeliveryWorker).processRequest(staleRequestId);

		int handled = pendingNotificationRecoveryService.reprocessStalePendingLogs(Instant.parse("2026-06-22T02:00:00Z"));

		assertThat(handled).isEqualTo(1);
		verify(notificationDeliveryWorker).processRequest(staleRequestId);
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(staleRequestId))
			.extracting(NotificationLog::sendStatus)
			.containsOnly(SendStatus.FAILED);
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(recentRequestId))
			.extracting(NotificationLog::sendStatus)
			.containsOnly(SendStatus.PENDING);
	}

	@Test
	void reprocessPendingLogs_marks_still_pending_logs_failed_when_worker_fails() {
		UUID requestId = UUID.randomUUID();
		savePendingLog(requestId, 1L, 1L, Instant.parse("2026-06-22T01:49:59Z"));
		doThrow(new RuntimeException("worker down")).when(notificationDeliveryWorker).processRequest(requestId);

		int handled = pendingNotificationRecoveryService.reprocessStalePendingLogs(Instant.parse("2026-06-22T02:00:00Z"));

		assertThat(handled).isEqualTo(1);
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(requestId))
			.extracting(NotificationLog::sendStatus, NotificationLog::failureReason)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(SendStatus.FAILED, "PENDING_REPROCESS_FAILED"));
	}

	@Test
	void reprocessPendingLogs_fails_closed_when_redis_lock_is_unavailable() {
		UUID requestId = UUID.randomUUID();
		savePendingLog(requestId, 1L, 1L, Instant.parse("2026-06-22T01:49:59Z"));
		notificationConcurrencyPort.fail();

		int handled = pendingNotificationRecoveryService.reprocessStalePendingLogs(Instant.parse("2026-06-22T02:00:00Z"));

		assertThat(handled).isZero();
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(requestId))
			.extracting(NotificationLog::sendStatus)
			.containsOnly(SendStatus.PENDING);
	}

	private void savePendingLog(UUID requestId, Long userId, Long campusId, Instant createdAt) {
		NotificationLog log = NotificationLog.pending(
			requestId,
			userId,
			campusId,
			NotificationType.DEVOTION_MISSING,
			LocalDate.of(2026, 6, 15),
			null,
			"알림",
			"본문"
		);
		notificationLogRepository.saveAndFlush(log);
		jdbcTemplate.update(
			"update notification_logs set created_at = ? where id = ?",
			Timestamp.from(createdAt),
			log.id()
		);
	}
}
