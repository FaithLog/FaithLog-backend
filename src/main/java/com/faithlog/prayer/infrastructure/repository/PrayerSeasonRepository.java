package com.faithlog.prayer.infrastructure.repository;

import com.faithlog.prayer.domain.entity.PrayerSeason;
import com.faithlog.prayer.domain.type.PrayerSeasonStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrayerSeasonRepository extends JpaRepository<PrayerSeason, Long> {

	Optional<PrayerSeason> findByCampusIdAndStatus(Long campusId, PrayerSeasonStatus status);

	Optional<PrayerSeason> findByCampusIdAndStatusAndEndDateIsNull(Long campusId, PrayerSeasonStatus status);

	boolean existsByCampusIdAndStatus(Long campusId, PrayerSeasonStatus status);
}
