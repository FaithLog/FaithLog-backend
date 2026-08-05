package com.faithlog.weeklymaterial.infrastructure.repository;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialNotificationOutbox;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyMaterialNotificationOutboxRepository
	extends JpaRepository<WeeklyMaterialNotificationOutbox, Long>, WeeklyMaterialNotificationOutboxRepositoryPort {
	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select outbox from WeeklyMaterialNotificationOutbox outbox where outbox.id = :id")
	Optional<WeeklyMaterialNotificationOutbox> findByIdForUpdate(@Param("id") Long id);

	@Override
	@Query("select outbox.id from WeeklyMaterialNotificationOutbox outbox where outbox.processedAt is null order by outbox.id")
	List<Long> findPendingIds(Pageable pageable);
}
