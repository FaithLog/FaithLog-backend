package com.faithlog.weeklymaterial.service.port;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialNotificationOutbox;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface WeeklyMaterialNotificationOutboxRepositoryPort {
	WeeklyMaterialNotificationOutbox save(WeeklyMaterialNotificationOutbox outbox);
	Optional<WeeklyMaterialNotificationOutbox> findById(Long id);
	Optional<WeeklyMaterialNotificationOutbox> findByIdForUpdate(Long id);
	Optional<WeeklyMaterialNotificationOutbox> findSlotForUpdate(
		Long campusId, LocalDate weekStartDate, WeeklyMaterialType materialType);
	List<Long> findPendingIds(Pageable pageable);
}
