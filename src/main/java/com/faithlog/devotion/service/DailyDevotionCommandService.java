package com.faithlog.devotion.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.devotion.service.command.UpdateDailyDevotionCommand;
import com.faithlog.devotion.service.result.DailyDevotionResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyDevotionCommandService {

	private final WeeklyDevotionRecordRepository weeklyRecordRepository;
	private final DevotionDailyCheckRepository dailyCheckRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;

	public DailyDevotionCommandService(
		WeeklyDevotionRecordRepository weeklyRecordRepository,
		DevotionDailyCheckRepository dailyCheckRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort
	) {
		this.weeklyRecordRepository = weeklyRecordRepository;
		this.dailyCheckRepository = dailyCheckRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
	}

	@Transactional
	public DailyDevotionResult updateDailyCheck(UpdateDailyDevotionCommand command) {
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		requireActiveCampusMember(command.campusId(), requester.userId());
		LocalDate weekStartDate = command.recordDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(command.campusId(), requester.userId(), weekStartDate)
			.orElse(null);
		validateNotSubmitted(weeklyRecord);
		if (weeklyRecord == null) {
			weeklyRecord = weeklyRecordRepository.save(WeeklyDevotionRecord.create(
				command.campusId(),
				requester.userId(),
				weekStartDate
			));
		}
		DevotionDailyCheck dailyCheck = upsertDailyCheck(
			weeklyRecord.id(),
			command.recordDate(),
			command.quietTimeChecked(),
			command.prayerChecked(),
			command.bibleReadingChecked()
		);

		refreshWeeklySummary(weeklyRecord);
		return DailyDevotionResult.of(weeklyRecord, dailyCheck);
	}

	private void validateNotSubmitted(WeeklyDevotionRecord weeklyRecord) {
		if (weeklyRecord != null && weeklyRecord.submittedAt() != null) {
			throw new BusinessException(ErrorCode.DEVOTION_WEEKLY_ALREADY_SUBMITTED);
		}
	}

	private DevotionDailyCheck upsertDailyCheck(
		Long weeklyRecordId,
		LocalDate recordDate,
		boolean quietTimeChecked,
		boolean prayerChecked,
		boolean bibleReadingChecked
	) {
		DevotionDailyCheck dailyCheck = dailyCheckRepository.findByWeeklyRecordIdAndRecordDate(weeklyRecordId, recordDate)
			.orElseGet(() -> dailyCheckRepository.save(DevotionDailyCheck.create(
				weeklyRecordId,
				recordDate,
				false,
				false,
				false
			)));
		dailyCheck.update(quietTimeChecked, prayerChecked, bibleReadingChecked);
		return dailyCheck;
	}

	private void refreshWeeklySummary(WeeklyDevotionRecord weeklyRecord) {
		List<DevotionDailyCheck> dailyChecks = dailyCheckRepository
			.findByWeeklyRecordIdOrderByRecordDateAsc(weeklyRecord.id());
		weeklyRecord.updateSummary(dailyChecks, weeklyRecord.saturdayLateMinutes());
	}

	private void requireActiveCampusMember(Long campusId, Long userId) {
		campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVOTION_ACCESS_FORBIDDEN));
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}
}
