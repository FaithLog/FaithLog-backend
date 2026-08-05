package com.faithlog.announcement.service;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementNotificationOutboxRepositoryPort;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementRetentionService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final AnnouncementRepositoryPort announcements;
	private final AnnouncementImageAttachmentService images;
	private final AnnouncementDocumentAttachmentService documents;
	private final AnnouncementNotificationOutboxRepositoryPort outboxes;

	public AnnouncementRetentionService(AnnouncementRepositoryPort announcements,
		AnnouncementImageAttachmentService images, AnnouncementDocumentAttachmentService documents,
		AnnouncementNotificationOutboxRepositoryPort outboxes) {
		this.announcements = announcements;
		this.images = images;
		this.documents = documents;
		this.outboxes = outboxes;
	}

	@Transactional
	public boolean deleteIfDue(Long announcementId, Instant now) {
		Announcement announcement = announcements.findByIdForUpdate(announcementId).orElse(null);
		if (announcement == null || !isDue(announcement, now)) return false;

		images.orphanAll(announcement.id(), announcement.campusId());
		documents.orphanAll(announcement.id(), announcement.campusId());
		outboxes.deleteByAnnouncementId(announcement.id());
		announcements.delete(announcement);
		return true;
	}

	private static boolean isDue(Announcement announcement, Instant now) {
		if (announcement.status() != AnnouncementStatus.PUBLISHED
			&& announcement.status() != AnnouncementStatus.ARCHIVED) return false;
		if (announcement.publishedAt() == null) return false;
		Instant dueAt = announcement.publishedAt().atZone(SEOUL).toLocalDate()
			.plusMonths(3).atStartOfDay(SEOUL).toInstant();
		return !dueAt.isAfter(now);
	}
}
