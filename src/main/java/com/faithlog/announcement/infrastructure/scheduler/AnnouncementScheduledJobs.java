package com.faithlog.announcement.infrastructure.scheduler;

import com.faithlog.announcement.service.AnnouncementNotificationOutboxProcessor;
import com.faithlog.announcement.service.ScheduledAnnouncementPublisher;
import com.faithlog.announcement.service.AnnouncementRetentionService;
import com.faithlog.announcement.service.port.AnnouncementNotificationOutboxRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(prefix = "faithlog.scheduler", name = "enabled", havingValue = "true")
public class AnnouncementScheduledJobs {
	private static final Logger log = LoggerFactory.getLogger(AnnouncementScheduledJobs.class);
	private static final int BATCH_SIZE = 100;
	private final AnnouncementRepositoryPort announcements;
	private final ScheduledAnnouncementPublisher publisher;
	private final AnnouncementNotificationOutboxRepositoryPort outboxes;
	private final AnnouncementNotificationOutboxProcessor processor;
	private final AnnouncementRetentionService retention;
	private final Clock clock;

	public AnnouncementScheduledJobs(
		AnnouncementRepositoryPort announcements,
		ScheduledAnnouncementPublisher publisher,
		AnnouncementNotificationOutboxRepositoryPort outboxes,
		AnnouncementNotificationOutboxProcessor processor,
		AnnouncementRetentionService retention,
		Clock clock
	) {
		this.announcements = announcements;
		this.publisher = publisher;
		this.outboxes = outboxes;
		this.processor = processor;
		this.retention = retention;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.announcement-publish-delay-ms:60000}")
	public void publishDueAnnouncements() {
		var now = clock.instant();
		announcements.findDueScheduledIds(now, PageRequest.of(0, BATCH_SIZE))
			.forEach(id -> publisher.publishIfDue(id, now));
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.announcement-outbox-delay-ms:60000}")
	public void deliverAnnouncementNotifications() {
		outboxes.findPendingIds(PageRequest.of(0, BATCH_SIZE)).forEach(id -> {
			try {
				processor.process(id);
			} catch (RuntimeException exception) {
				log.warn("Announcement notification outbox processing will retry. outboxId={}", id);
			}
		});
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.announcement-retention-delay-ms:3600000}")
	public void physicallyDeleteExpiredAnnouncements() {
		var now = clock.instant();
		announcements.findDuePhysicalDeletionIds(now, PageRequest.of(0, BATCH_SIZE)).forEach(id -> {
			try {
				retention.deleteIfDue(id, now);
			} catch (RuntimeException exception) {
				log.warn("Announcement physical deletion will retry. announcementId={}", id);
			}
		});
	}
}
