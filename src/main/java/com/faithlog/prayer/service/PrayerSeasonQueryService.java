package com.faithlog.prayer.service;

import com.faithlog.prayer.domain.type.PrayerSeasonStatus;
import com.faithlog.prayer.infrastructure.repository.PrayerSeasonRepository;
import com.faithlog.prayer.service.result.PrayerSeasonResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrayerSeasonQueryService {

	private final PrayerSeasonRepository seasonRepository;
	private final PrayerAccessSupport accessSupport;

	public PrayerSeasonQueryService(
		PrayerSeasonRepository seasonRepository,
		PrayerAccessSupport accessSupport
	) {
		this.seasonRepository = seasonRepository;
		this.accessSupport = accessSupport;
	}

	@Transactional(readOnly = true)
	public PrayerSeasonResult getCurrentSeason(Long campusId, Long requesterId) {
		accessSupport.requirePrayerManager(campusId, requesterId);
		return seasonRepository.findByCampusIdAndStatusAndEndDateIsNull(campusId, PrayerSeasonStatus.ACTIVE)
			.map(PrayerSeasonResult::from)
			.orElse(null);
	}
}
