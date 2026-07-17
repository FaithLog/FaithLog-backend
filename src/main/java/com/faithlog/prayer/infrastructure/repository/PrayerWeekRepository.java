package com.faithlog.prayer.infrastructure.repository;

import com.faithlog.prayer.domain.entity.PrayerWeek;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrayerWeekRepository extends JpaRepository<PrayerWeek, Long> {

	Optional<PrayerWeek> findByCampusIdAndSeasonIdAndWeekStartDate(Long campusId, Long seasonId, LocalDate weekStartDate);
}
