package com.faithlog.weeklymaterial.infrastructure.repository;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialGlobalLock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WeeklyMaterialGlobalLockRepository extends JpaRepository<WeeklyMaterialGlobalLock, Short> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select lock from WeeklyMaterialGlobalLock lock where lock.id = 1")
	Optional<WeeklyMaterialGlobalLock> findSingletonForUpdate();
}
