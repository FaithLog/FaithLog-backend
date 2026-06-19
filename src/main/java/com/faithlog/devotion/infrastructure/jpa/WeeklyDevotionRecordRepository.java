package com.faithlog.devotion.infrastructure.jpa;

import com.faithlog.devotion.domain.WeeklyDevotionRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyDevotionRecordRepository extends JpaRepository<WeeklyDevotionRecord, Long> {

	Optional<WeeklyDevotionRecord> findByCampusIdAndUserIdAndWeekStartDate(Long campusId, Long userId, LocalDate weekStartDate);

	List<WeeklyDevotionRecord> findByCampusIdAndWeekStartDate(Long campusId, LocalDate weekStartDate);

	List<WeeklyDevotionRecord> findByCampusIdAndUserIdAndWeekStartDateLessThanEqualAndWeekEndDateGreaterThanEqualOrderByWeekStartDateAsc(
		Long campusId,
		Long userId,
		LocalDate weekStartDate,
		LocalDate weekEndDate
	);
}
