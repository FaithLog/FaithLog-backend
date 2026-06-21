package com.faithlog.prayer.infrastructure.jpa;

import com.faithlog.prayer.domain.PrayerSubmission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrayerSubmissionRepository extends JpaRepository<PrayerSubmission, Long> {

	List<PrayerSubmission> findByPrayerWeekId(Long prayerWeekId);

	Optional<PrayerSubmission> findByPrayerWeekIdAndUserId(Long prayerWeekId, Long userId);
}
