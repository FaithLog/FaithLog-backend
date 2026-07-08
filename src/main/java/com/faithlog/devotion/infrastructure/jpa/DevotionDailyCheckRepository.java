package com.faithlog.devotion.infrastructure.jpa;

import com.faithlog.devotion.domain.DevotionDailyCheck;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DevotionDailyCheckRepository extends JpaRepository<DevotionDailyCheck, Long> {

	Optional<DevotionDailyCheck> findByWeeklyRecordIdAndRecordDate(Long weeklyRecordId, LocalDate recordDate);

	List<DevotionDailyCheck> findByWeeklyRecordIdOrderByRecordDateAsc(Long weeklyRecordId);

	List<DevotionDailyCheck> findByWeeklyRecordIdInAndRecordDateBetweenOrderByRecordDateAsc(
		List<Long> weeklyRecordIds,
		LocalDate startDate,
		LocalDate endDate
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		delete from DevotionDailyCheck dailyCheck
		where dailyCheck.recordDate between :startDate and :endDate
		""")
	int deleteByRecordDateBetween(
		@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate
	);
}
