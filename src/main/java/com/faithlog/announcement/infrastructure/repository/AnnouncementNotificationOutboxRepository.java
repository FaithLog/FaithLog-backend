package com.faithlog.announcement.infrastructure.repository;

import com.faithlog.announcement.domain.entity.AnnouncementNotificationOutbox;
import com.faithlog.announcement.service.port.AnnouncementNotificationOutboxRepositoryPort;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementNotificationOutboxRepository
	extends JpaRepository<AnnouncementNotificationOutbox, Long>, AnnouncementNotificationOutboxRepositoryPort {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select outbox from AnnouncementNotificationOutbox outbox where outbox.id = :outboxId")
	Optional<AnnouncementNotificationOutbox> findByIdForUpdate(@Param("outboxId") Long outboxId);
}
