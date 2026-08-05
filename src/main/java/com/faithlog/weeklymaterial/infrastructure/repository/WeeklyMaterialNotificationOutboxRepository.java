package com.faithlog.weeklymaterial.infrastructure.repository;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialNotificationOutbox;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyMaterialNotificationOutboxRepository
	extends JpaRepository<WeeklyMaterialNotificationOutbox, Long>, WeeklyMaterialNotificationOutboxRepositoryPort {
	@Override
	@Query("""
		select new com.faithlog.weeklymaterial.service.port.WeeklyMaterialOutboxSnapshot(
			outbox.id, outbox.campusId, outbox.weekStartDate, outbox.materialType, outbox.processedAt)
		from WeeklyMaterialNotificationOutbox outbox where outbox.id = :id
		""")
	java.util.Optional<com.faithlog.weeklymaterial.service.port.WeeklyMaterialOutboxSnapshot> findSnapshotById(
		@Param("id") Long id);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select outbox from WeeklyMaterialNotificationOutbox outbox where outbox.id = :id")
	Optional<WeeklyMaterialNotificationOutbox> findByIdForUpdate(@Param("id") Long id);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select outbox from WeeklyMaterialNotificationOutbox outbox "
		+ "where outbox.campusId = :campusId and outbox.weekStartDate = :weekStartDate "
		+ "and outbox.materialType = :materialType")
	Optional<WeeklyMaterialNotificationOutbox> findSlotForUpdate(@Param("campusId") Long campusId,
		@Param("weekStartDate") LocalDate weekStartDate, @Param("materialType") WeeklyMaterialType materialType);

	@Override
	@Query("select outbox.id from WeeklyMaterialNotificationOutbox outbox where outbox.processedAt is null order by outbox.id")
	List<Long> findPendingIds(Pageable pageable);
}
