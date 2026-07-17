package com.faithlog.prayer.service;

import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.prayer.domain.entity.PrayerSeason;
import com.faithlog.prayer.domain.entity.PrayerSubmission;
import com.faithlog.prayer.domain.entity.PrayerWeek;
import com.faithlog.prayer.domain.type.PrayerSeasonStatus;
import com.faithlog.prayer.infrastructure.repository.PrayerSeasonRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerSubmissionRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerWeekRepository;
import com.faithlog.prayer.service.result.PrayerWeekBoardResult;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrayerWeekBoardQueryService {

	private final PrayerSeasonRepository seasonRepository;
	private final PrayerWeekRepository weekRepository;
	private final PrayerSubmissionRepository submissionRepository;
	private final PrayerAccessSupport accessSupport;
	private final PrayerBoardAssembler boardAssembler;

	public PrayerWeekBoardQueryService(
		PrayerSeasonRepository seasonRepository,
		PrayerWeekRepository weekRepository,
		PrayerSubmissionRepository submissionRepository,
		PrayerAccessSupport accessSupport,
		PrayerBoardAssembler boardAssembler
	) {
		this.seasonRepository = seasonRepository;
		this.weekRepository = weekRepository;
		this.submissionRepository = submissionRepository;
		this.accessSupport = accessSupport;
		this.boardAssembler = boardAssembler;
	}

	@Transactional(readOnly = true)
	public PrayerWeekBoardResult getWeeklyBoard(Long campusId, LocalDate weekStartDate, Long requesterId) {
		validateWeekStartDate(weekStartDate);
		CampusUserLookupResult requester = accessSupport.requirePrayerReader(campusId, requesterId);
		PrayerSeason season = seasonRepository
			.findByCampusIdAndStatusAndEndDateIsNull(campusId, PrayerSeasonStatus.ACTIVE)
			.orElse(null);
		if (season == null) {
			return PrayerWeekBoardResult.empty(campusId, weekStartDate);
		}
		PrayerWeek week = weekRepository
			.findByCampusIdAndSeasonIdAndWeekStartDate(campusId, season.id(), weekStartDate)
			.orElse(null);
		List<PrayerSubmission> submissions = week == null
			? List.of()
			: submissionRepository.findByPrayerWeekId(week.id());
		return boardAssembler.buildBoard(campusId, season, weekStartDate, week, submissions, requester);
	}

	private void validateWeekStartDate(LocalDate weekStartDate) {
		if (weekStartDate == null || weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new BusinessException(ErrorCode.PRAYER_INVALID_WEEK_START_DATE);
		}
	}
}
