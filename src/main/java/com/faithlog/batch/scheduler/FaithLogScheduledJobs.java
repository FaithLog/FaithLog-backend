package com.faithlog.batch.scheduler;

import com.faithlog.batch.application.FcmTokenCleanupService;
import com.faithlog.batch.application.PollAutomationService;
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
	private final FcmTokenCleanupService fcmTokenCleanupService;

	public FaithLogScheduledJobs(
		PollAutomationService pollAutomationService,
		FcmTokenCleanupService fcmTokenCleanupService
	) {
		this.pollAutomationService = pollAutomationService;
		this.fcmTokenCleanupService = fcmTokenCleanupService;
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.poll-auto-create-delay-ms:60000}", zone = SEOUL_ZONE)
	public void createDuePolls() {
		runJob("poll-auto-create", () -> pollAutomationService.createDuePolls(Instant.now()));
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.coffee-poll-close-delay-ms:60000}", zone = SEOUL_ZONE)
	public void closeDueCoffeePolls() {
		runJob("coffee-poll-close", () -> pollAutomationService.closeDueCoffeePolls(Instant.now()));
	}

	@Scheduled(cron = "${faithlog.scheduler.fcm-token-cleanup-cron:0 20 3 * * *}", zone = SEOUL_ZONE)
	public void cleanupStaleFcmTokens() {
		runJob("fcm-token-cleanup", () -> fcmTokenCleanupService.deactivateStaleTokens(Instant.now()));
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
