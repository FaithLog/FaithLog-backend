package com.faithlog.weeklymaterial.infrastructure.scheduler;

import com.faithlog.weeklymaterial.service.WeeklyMaterialNotificationOutboxProcessor;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "faithlog.scheduler", name = "enabled", havingValue = "true")
public class WeeklyMaterialScheduledJobs {
	private static final Logger log = LoggerFactory.getLogger(WeeklyMaterialScheduledJobs.class);
	private final WeeklyMaterialNotificationOutboxRepositoryPort outboxes;
	private final WeeklyMaterialNotificationOutboxProcessor processor;
	public WeeklyMaterialScheduledJobs(WeeklyMaterialNotificationOutboxRepositoryPort outboxes,
		WeeklyMaterialNotificationOutboxProcessor processor) {
		this.outboxes = outboxes; this.processor = processor;
	}
	@Scheduled(fixedDelayString = "${faithlog.scheduler.weekly-material-outbox-delay-ms:60000}")
	public void deliverNotifications() {
		outboxes.findPendingIds(PageRequest.of(0, 100)).forEach(id -> {
			try { processor.process(id); }
			catch (RuntimeException exception) {
				log.warn("Weekly material notification outbox processing will retry. outboxId={}", id);
			}
		});
	}
}
