package com.faithlog.devotion.infrastructure.jpa;

import com.faithlog.devotion.domain.DevotionDailyCheck;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevotionDailyCheckRepository extends JpaRepository<DevotionDailyCheck, Long> {

	Optional<DevotionDailyCheck> findByWeeklyRecordIdAndRecordDate(Long weeklyRecordId, LocalDate recordDate);

	List<DevotionDailyCheck> findByWeeklyRecordIdOrderByRecordDateAsc(Long weeklyRecordId);

	List<DevotionDailyCheck> findByWeeklyRecordIdInAndRecordDateBetweenOrderByRecordDateAsc(
		List<Long> weeklyRecordIds,
		LocalDate startDate,
		LocalDate endDate
	);
}
