package com.faithlog.devotion.service;

import com.faithlog.devotion.service.query.GetMyMonthlyDevotionSummaryQuery;
import com.faithlog.devotion.service.result.MyMonthlyDevotionSummaryResult;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevotionMonthlySummaryQueryService {

	private final WeeklyDevotionRecordRepository weeklyRecordRepository;
	private final DevotionDailyCheckRepository dailyCheckRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;

	public DevotionMonthlySummaryQueryService(
		WeeklyDevotionRecordRepository weeklyRecordRepository,
		DevotionDailyCheckRepository dailyCheckRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort
	) {
		this.weeklyRecordRepository = weeklyRecordRepository;
		this.dailyCheckRepository = dailyCheckRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
	}

	@Transactional(readOnly = true)
	public MyMonthlyDevotionSummaryResult getMyMonthlySummary(GetMyMonthlyDevotionSummaryQuery query) {
		YearMonth yearMonth = yearMonth(query.year(), query.month());
		LocalDate monthStart = yearMonth.atDay(1);
		LocalDate monthEnd = yearMonth.atEndOfMonth();
		CampusUserLookupResult requester = getActiveUser(query.requesterId());
		requireActiveCampusMember(query.campusId(), requester.userId());
		Campus campus = getCampusOrThrow(query.campusId());
		List<WeeklyDevotionRecord> weeklyRecords = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDateLessThanEqualAndWeekEndDateGreaterThanEqualOrderByWeekStartDateAsc(
				query.campusId(),
				requester.userId(),
				monthEnd,
				monthStart
			);
		Map<Long, List<DevotionDailyCheck>> dailyChecksByWeeklyRecordId = dailyChecksByWeeklyRecordId(
			weeklyRecords,
			monthStart,
			monthEnd
		);
		List<MyMonthlyDevotionSummaryResult.WeeklyRecord> weeklyResults = weeklyRecords.stream()
			.map(weeklyRecord -> toWeeklyRecord(
				weeklyRecord,
				dailyChecksByWeeklyRecordId.getOrDefault(weeklyRecord.id(), List.of()),
				monthStart,
				monthEnd
			))
			.toList();
		MyMonthlyDevotionSummaryResult.Devotion devotion = new MyMonthlyDevotionSummaryResult.Devotion(
			weeklyResults.stream().mapToInt(MyMonthlyDevotionSummaryResult.WeeklyRecord::quietTimeCount).sum(),
			weeklyResults.stream().mapToInt(MyMonthlyDevotionSummaryResult.WeeklyRecord::prayerCount).sum(),
			weeklyResults.stream().mapToInt(MyMonthlyDevotionSummaryResult.WeeklyRecord::bibleReadingCount).sum(),
			weeklyResults.stream().mapToInt(MyMonthlyDevotionSummaryResult.WeeklyRecord::saturdayLateMinutes).sum()
		);

		return new MyMonthlyDevotionSummaryResult(
			campus.id(),
			campus.name(),
			campus.region(),
			requester.userId(),
			requester.name(),
			yearMonth.getYear(),
			yearMonth.getMonthValue(),
			devotion,
			weeklyResults
		);
	}

	private Map<Long, List<DevotionDailyCheck>> dailyChecksByWeeklyRecordId(
		List<WeeklyDevotionRecord> weeklyRecords,
		LocalDate monthStart,
		LocalDate monthEnd
	) {
		List<Long> weeklyRecordIds = weeklyRecords.stream()
			.map(WeeklyDevotionRecord::id)
			.toList();
		if (weeklyRecordIds.isEmpty()) {
			return Map.of();
		}
		return dailyCheckRepository
			.findByWeeklyRecordIdInAndRecordDateBetweenOrderByRecordDateAsc(weeklyRecordIds, monthStart, monthEnd)
			.stream()
			.collect(Collectors.groupingBy(DevotionDailyCheck::weeklyRecordId));
	}

	private MyMonthlyDevotionSummaryResult.WeeklyRecord toWeeklyRecord(
		WeeklyDevotionRecord weeklyRecord,
		List<DevotionDailyCheck> dailyChecksInMonth,
		LocalDate monthStart,
		LocalDate monthEnd
	) {
		return new MyMonthlyDevotionSummaryResult.WeeklyRecord(
			weeklyRecord.id(),
			weeklyRecord.weekStartDate(),
			weeklyRecord.weekEndDate(),
			(int) dailyChecksInMonth.stream().filter(DevotionDailyCheck::quietTimeChecked).count(),
			(int) dailyChecksInMonth.stream().filter(DevotionDailyCheck::prayerChecked).count(),
			(int) dailyChecksInMonth.stream().filter(DevotionDailyCheck::bibleReadingChecked).count(),
			saturdayLateMinutesInMonth(weeklyRecord, monthStart, monthEnd),
			weeklyRecord.submittedAt()
		);
	}

	private int saturdayLateMinutesInMonth(
		WeeklyDevotionRecord weeklyRecord,
		LocalDate monthStart,
		LocalDate monthEnd
	) {
		LocalDate saturday = weeklyRecord.weekStartDate().plusDays(5);
		if (saturday.isBefore(monthStart) || saturday.isAfter(monthEnd)) {
			return 0;
		}
		return weeklyRecord.saturdayLateMinutes();
	}

	private YearMonth yearMonth(int year, int month) {
		if (year <= 0) {
			throw new BusinessException(ErrorCode.DEVOTION_INVALID_YEAR_MONTH);
		}
		try {
			return YearMonth.of(year, month);
		} catch (DateTimeException exception) {
			throw new BusinessException(ErrorCode.DEVOTION_INVALID_YEAR_MONTH);
		}
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

	private Campus getCampusOrThrow(Long campusId) {
		return campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}
}
