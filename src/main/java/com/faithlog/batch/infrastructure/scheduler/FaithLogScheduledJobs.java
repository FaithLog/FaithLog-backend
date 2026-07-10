package com.faithlog.batch.infrastructure.scheduler;

import com.faithlog.batch.service.AutomaticNotificationService;
import com.faithlog.batch.service.DataRetentionCleanupService;
import com.faithlog.batch.service.FcmTokenCleanupService;
import com.faithlog.batch.service.PendingNotificationRecoveryService;
import com.faithlog.batch.service.PollAutomationService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "faithlog.scheduler", name = "enabled", havingValue = "true")
public class FaithLogScheduledJobs {

	private static final Logger log = LoggerFactory.getLogger(FaithLogScheduledJobs.class);
	private static final String SEOUL_ZONE = "Asia/Seoul";

	private final PollAutomationService pollAutomationService;
	private final DataRetentionCleanupService dataRetentionCleanupService;
	private final FcmTokenCleanupService fcmTokenCleanupService;
	private final AutomaticNotificationService automaticNotificationService;
	private final PendingNotificationRecoveryService pendingNotificationRecoveryService;

	public FaithLogScheduledJobs(
		PollAutomationService pollAutomationService,
		DataRetentionCleanupService dataRetentionCleanupService,
		FcmTokenCleanupService fcmTokenCleanupService,
		AutomaticNotificationService automaticNotificationService,
		PendingNotificationRecoveryService pendingNotificationRecoveryService
	) {
		this.pollAutomationService = pollAutomationService;
		this.dataRetentionCleanupService = dataRetentionCleanupService;
		this.fcmTokenCleanupService = fcmTokenCleanupService;
		this.automaticNotificationService = automaticNotificationService;
		this.pendingNotificationRecoveryService = pendingNotificationRecoveryService;
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.poll-auto-create-delay-ms:60000}", zone = SEOUL_ZONE)
	public void createDuePolls() {
		runJob("poll-auto-create", () -> pollAutomationService.createDuePolls(Instant.now()));
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.coffee-poll-close-delay-ms:60000}", zone = SEOUL_ZONE)
	public void closeDueCoffeePolls() {
		runJob("coffee-poll-close", () -> pollAutomationService.closeDueCoffeePolls(Instant.now()));
	}

	@Scheduled(cron = "${faithlog.scheduler.data-retention-cleanup-cron:0 30 4 * * *}", zone = SEOUL_ZONE)
	public void cleanupDataRetention() {
		runJob("data-retention-cleanup", () -> dataRetentionCleanupService.cleanupDueData(Instant.now()).totalDeleted());
	}

	@Scheduled(cron = "${faithlog.scheduler.fcm-token-cleanup-cron:0 20 3 * * *}", zone = SEOUL_ZONE)
	public void cleanupStaleFcmTokens() {
		runJob("fcm-token-cleanup", () -> fcmTokenCleanupService.deactivateStaleTokens(Instant.now()));
	}

	@Scheduled(cron = "${faithlog.scheduler.devotion-missing-cron:0 0 11 * * *}", zone = SEOUL_ZONE)
	public void sendDevotionMissingReminders() {
		runJob("devotion-missing", () -> automaticNotificationService.sendDevotionMissingReminders(Instant.now()));
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.poll-missing-delay-ms:60000}", zone = SEOUL_ZONE)
	public void sendPollMissingReminders() {
		runJob("poll-missing", () -> automaticNotificationService.sendPollMissingReminders(Instant.now()));
	}

	@Scheduled(cron = "${faithlog.scheduler.payment-unpaid-cron:0 0 12 * * *}", zone = SEOUL_ZONE)
	public void sendPaymentUnpaidReminders() {
		runJob("payment-unpaid", () -> automaticNotificationService.sendPaymentUnpaidReminders(Instant.now()));
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.pending-notification-recovery-delay-ms:60000}", zone = SEOUL_ZONE)
	public void reprocessPendingNotificationLogs() {
		runJob("pending-notification-reprocess", () -> pendingNotificationRecoveryService.reprocessStalePendingLogs(Instant.now()));
	}

	private void runJob(String jobName, ScheduledJob job) {
		try {
			int changedCount = job.run();
			log.info("FaithLog scheduled job completed: jobName={}, changedCount={}", jobName, changedCount);
		} catch (RuntimeException exception) {
			log.error("FaithLog scheduled job failed: jobName={}", jobName, exception);
		}
	}

	@FunctionalInterface
	private interface ScheduledJob {
		int run();
	}
}
