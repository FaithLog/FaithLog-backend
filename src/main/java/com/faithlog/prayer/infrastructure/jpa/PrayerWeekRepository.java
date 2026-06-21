package com.faithlog.prayer.infrastructure.jpa;

import com.faithlog.prayer.domain.PrayerWeek;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrayerWeekRepository extends JpaRepository<PrayerWeek, Long> {

	Optional<PrayerWeek> findByCampusIdAndSeasonIdAndWeekStartDate(Long campusId, Long seasonId, LocalDate weekStartDate);
}
