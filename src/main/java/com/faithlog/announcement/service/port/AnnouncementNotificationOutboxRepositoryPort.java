package com.faithlog.announcement.service.port;

import com.faithlog.announcement.domain.entity.AnnouncementNotificationOutbox;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface AnnouncementNotificationOutboxRepositoryPort {

	AnnouncementNotificationOutbox save(AnnouncementNotificationOutbox outbox);

	Optional<AnnouncementNotificationOutbox> findByIdForUpdate(Long outboxId);
	List<Long> findPendingIds(Pageable pageable);
}
