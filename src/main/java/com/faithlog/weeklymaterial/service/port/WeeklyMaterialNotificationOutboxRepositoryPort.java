package com.faithlog.weeklymaterial.service.port;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialNotificationOutbox;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface WeeklyMaterialNotificationOutboxRepositoryPort {
	WeeklyMaterialNotificationOutbox save(WeeklyMaterialNotificationOutbox outbox);
	Optional<WeeklyMaterialNotificationOutbox> findByIdForUpdate(Long id);
	List<Long> findPendingIds(Pageable pageable);
}
