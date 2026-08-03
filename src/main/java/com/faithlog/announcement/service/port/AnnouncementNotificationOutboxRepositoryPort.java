package com.faithlog.announcement.service.port;

import com.faithlog.announcement.domain.entity.AnnouncementNotificationOutbox;
import java.util.Optional;

public interface AnnouncementNotificationOutboxRepositoryPort {

	AnnouncementNotificationOutbox save(AnnouncementNotificationOutbox outbox);

	Optional<AnnouncementNotificationOutbox> findByIdForUpdate(Long outboxId);
}
