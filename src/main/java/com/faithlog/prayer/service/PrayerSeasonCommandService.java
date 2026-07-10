package com.faithlog.prayer.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.prayer.domain.entity.PrayerSeason;
import com.faithlog.prayer.domain.type.PrayerSeasonStatus;
import com.faithlog.prayer.infrastructure.repository.PrayerSeasonRepository;
import com.faithlog.prayer.service.command.ClosePrayerSeasonCommand;
import com.faithlog.prayer.service.command.CreatePrayerSeasonCommand;
import com.faithlog.prayer.service.result.PrayerSeasonResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrayerSeasonCommandService {

	private final PrayerSeasonRepository seasonRepository;
	private final PrayerAccessSupport accessSupport;

	public PrayerSeasonCommandService(
		PrayerSeasonRepository seasonRepository,
		PrayerAccessSupport accessSupport
	) {
		this.seasonRepository = seasonRepository;
		this.accessSupport = accessSupport;
	}

	@Transactional
	public PrayerSeasonResult createSeason(CreatePrayerSeasonCommand command) {
		accessSupport.requirePrayerManager(command.campusId(), command.requesterId());
		if (seasonRepository.existsByCampusIdAndStatus(command.campusId(), PrayerSeasonStatus.ACTIVE)) {
			throw new BusinessException(ErrorCode.PRAYER_ACTIVE_SEASON_ALREADY_EXISTS);
		}
		PrayerSeason season = seasonRepository.save(PrayerSeason.create(
			command.campusId(),
			command.name(),
			command.startDate(),
			command.requesterId()
		));
		return PrayerSeasonResult.from(season);
	}

	@Transactional
	public PrayerSeasonResult closeSeason(ClosePrayerSeasonCommand command) {
		PrayerSeason season = seasonRepository.findById(command.seasonId())
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_SEASON_NOT_FOUND));
		accessSupport.requirePrayerManager(season.campusId(), command.requesterId());
		season.close(command.endDate());
		return PrayerSeasonResult.from(season);
	}
}
