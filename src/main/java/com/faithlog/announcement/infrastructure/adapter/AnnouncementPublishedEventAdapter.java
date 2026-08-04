package com.faithlog.announcement.infrastructure.adapter;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.domain.entity.AnnouncementNotificationOutbox;
import com.faithlog.announcement.service.port.AnnouncementNotificationOutboxRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementPublishedEventPort;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementPublishedEventAdapter implements AnnouncementPublishedEventPort {

	private final AnnouncementNotificationOutboxRepositoryPort outboxRepository;

	public AnnouncementPublishedEventAdapter(AnnouncementNotificationOutboxRepositoryPort outboxRepository) {
		this.outboxRepository = outboxRepository;
	}

	@Override
	public void recordPublished(Announcement announcement, AnnouncementCategory category) {
		outboxRepository.save(AnnouncementNotificationOutbox.create(
			announcement.id(),
			announcement.campusId(),
			announcement.categoryId(),
			announcement.authorId(),
			category.name(),
			announcement.title(),
			announcement.publishedAt()
		));
	}
}
