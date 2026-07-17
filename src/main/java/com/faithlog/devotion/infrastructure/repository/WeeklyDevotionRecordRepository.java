package com.faithlog.devotion.infrastructure.repository;

import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyDevotionRecordRepository extends JpaRepository<WeeklyDevotionRecord, Long> {

	Optional<WeeklyDevotionRecord> findByCampusIdAndUserIdAndWeekStartDate(Long campusId, Long userId, LocalDate weekStartDate);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select record from WeeklyDevotionRecord record
		where record.campusId = :campusId
			and record.userId = :userId
			and record.weekStartDate = :weekStartDate
		""")
	Optional<WeeklyDevotionRecord> findByCampusIdAndUserIdAndWeekStartDateForUpdate(
		@Param("campusId") Long campusId,
		@Param("userId") Long userId,
		@Param("weekStartDate") LocalDate weekStartDate
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select record from WeeklyDevotionRecord record where record.id = :weeklyRecordId")
	Optional<WeeklyDevotionRecord> findByIdForUpdate(@Param("weeklyRecordId") Long weeklyRecordId);

	List<WeeklyDevotionRecord> findByCampusIdAndWeekStartDate(Long campusId, LocalDate weekStartDate);

	List<WeeklyDevotionRecord> findByCampusIdAndUserIdAndWeekStartDateLessThanEqualAndWeekEndDateGreaterThanEqualOrderByWeekStartDateAsc(
		Long campusId,
		Long userId,
		LocalDate weekStartDate,
		LocalDate weekEndDate
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		delete from WeeklyDevotionRecord weeklyRecord
		where weeklyRecord.weekStartDate between :startDate and :endDate
		""")
	int deleteByWeekStartDateBetween(
		@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate
	);
}
