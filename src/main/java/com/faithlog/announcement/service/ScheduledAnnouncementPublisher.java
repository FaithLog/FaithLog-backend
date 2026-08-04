package com.faithlog.announcement.service;

import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementPublishedEventPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduledAnnouncementPublisher {
	private final AnnouncementRepositoryPort announcements;
	private final AnnouncementCategoryRepositoryPort categories;
	private final AnnouncementPublishedEventPort events;

	public ScheduledAnnouncementPublisher(
		AnnouncementRepositoryPort announcements,
		AnnouncementCategoryRepositoryPort categories,
		AnnouncementPublishedEventPort events
	) {
		this.announcements = announcements;
		this.categories = categories;
		this.events = events;
	}

	@Transactional
	public boolean publishIfDue(Long announcementId, Instant now) {
		var announcement = announcements.findByIdForUpdate(announcementId).orElse(null);
		if (announcement == null || announcement.status() != AnnouncementStatus.SCHEDULED
			|| announcement.publishAt().isAfter(now)) {
			return false;
		}
		var category = categories.findByCampusIdAndId(announcement.campusId(), announcement.categoryId()).orElseThrow();
		announcement.publish(now);
		events.recordPublished(announcement, category);
		return true;
	}
}
