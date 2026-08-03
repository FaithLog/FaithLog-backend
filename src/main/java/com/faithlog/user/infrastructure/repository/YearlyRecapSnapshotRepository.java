package com.faithlog.user.infrastructure.repository;

import com.faithlog.user.domain.entity.YearlyRecapSnapshot;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YearlyRecapSnapshotRepository extends JpaRepository<YearlyRecapSnapshot, Long> {

	Optional<YearlyRecapSnapshot> findByUserIdAndRecapYear(Long userId, int recapYear);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select snapshot from YearlyRecapSnapshot snapshot where snapshot.userId = :userId and snapshot.recapYear = :recapYear")
	Optional<YearlyRecapSnapshot> findByUserIdAndRecapYearForUpdate(
		@Param("userId") Long userId,
		@Param("recapYear") int recapYear
	);
}
