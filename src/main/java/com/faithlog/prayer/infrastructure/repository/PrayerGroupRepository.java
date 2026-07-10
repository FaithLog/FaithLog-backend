package com.faithlog.prayer.infrastructure.repository;

import com.faithlog.prayer.domain.entity.PrayerGroup;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrayerGroupRepository extends JpaRepository<PrayerGroup, Long> {

	List<PrayerGroup> findBySeasonIdAndIsActiveTrueOrderBySortOrderAscIdAsc(Long seasonId);

	List<PrayerGroup> findByIdIn(Collection<Long> ids);
}
