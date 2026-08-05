package com.faithlog.weeklymaterial.infrastructure.scheduler;

import com.faithlog.weeklymaterial.service.WeeklyMaterialNotificationOutboxProcessor;
import com.faithlog.weeklymaterial.service.WeeklyMaterialRetentionService;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
	private final WeeklyMaterialRepositoryPort materials;
	private final WeeklyMaterialRetentionService retention;
	private final Clock clock;
	public WeeklyMaterialScheduledJobs(WeeklyMaterialNotificationOutboxRepositoryPort outboxes,
		WeeklyMaterialNotificationOutboxProcessor processor, WeeklyMaterialRepositoryPort materials,
		WeeklyMaterialRetentionService retention, Clock clock) {
		this.outboxes = outboxes;
		this.processor = processor;
		this.materials = materials;
		this.retention = retention;
		this.clock = clock;
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

	@Scheduled(fixedDelayString = "${faithlog.scheduler.weekly-material-retention-delay-ms:3600000}")
	public void physicallyDeleteExpiredMaterials() {
		LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
		materials.findDuePhysicalDeletionIds(today, PageRequest.of(0, 100)).forEach(id -> {
			try {
				retention.deleteIfDue(id, today);
			} catch (RuntimeException exception) {
				log.warn("Weekly material physical deletion will retry. weeklyMaterialId={}", id);
			}
		});
	}
}
