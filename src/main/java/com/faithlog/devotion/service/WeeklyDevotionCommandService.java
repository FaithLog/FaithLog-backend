package com.faithlog.devotion.service;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.devotion.domain.DevotionFineCalculator;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.PenaltyRule;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.domain.type.DevotionFineCalculationInput;
import com.faithlog.devotion.domain.type.DevotionFineCalculationResult;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.PenaltyRuleRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.devotion.service.command.DevotionDailyCheckCommand;
import com.faithlog.devotion.service.command.UpdateWeeklyDevotionCommand;
import com.faithlog.devotion.service.port.DevotionPenaltyChargeCommand;
import com.faithlog.devotion.service.port.DevotionPenaltyChargePort;
import com.faithlog.devotion.service.result.WeeklyDevotionResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyDevotionCommandService {

	private static final String PENALTY_CHARGE_TITLE = "경건생활 벌금";
	private static final int MAX_SATURDAY_LATE_MINUTES = 1_440;

	private final WeeklyDevotionRecordRepository weeklyRecordRepository;
	private final DevotionDailyCheckRepository dailyCheckRepository;
	private final PenaltyRuleRepository penaltyRuleRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final DevotionFineCalculator fineCalculator;
	private final DevotionPenaltyChargePort penaltyChargePort;

	public WeeklyDevotionCommandService(
		WeeklyDevotionRecordRepository weeklyRecordRepository,
		DevotionDailyCheckRepository dailyCheckRepository,
		PenaltyRuleRepository penaltyRuleRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		DevotionFineCalculator fineCalculator,
		DevotionPenaltyChargePort penaltyChargePort
	) {
		this.weeklyRecordRepository = weeklyRecordRepository;
		this.dailyCheckRepository = dailyCheckRepository;
		this.penaltyRuleRepository = penaltyRuleRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.fineCalculator = fineCalculator;
		this.penaltyChargePort = penaltyChargePort;
	}

	@Transactional
	public WeeklyDevotionResult updateWeeklyCheck(UpdateWeeklyDevotionCommand command) {
		validateMonday(command.weekStartDate());
		validateSaturdayLateMinutes(command.saturdayLateMinutes());
		validateDailyChecksInWeek(command.weekStartDate(), command.dailyChecks());
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		requireActiveCampusMember(command.campusId(), requester.userId());
		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDateForUpdate(
				command.campusId(), requester.userId(), command.weekStartDate())
			.orElse(null);
		validateNotSubmitted(weeklyRecord);
		if (weeklyRecord == null) {
			weeklyRecord = weeklyRecordRepository.save(WeeklyDevotionRecord.create(
				command.campusId(),
				requester.userId(),
				command.weekStartDate()
			));
		}
		WeeklyDevotionRecord targetWeeklyRecord = weeklyRecord;
		Map<LocalDate, DevotionDailyCheckCommand> requestedChecks = command.dailyChecks().stream()
			.collect(Collectors.toMap(
				DevotionDailyCheckCommand::recordDate,
				Function.identity(),
				(first, second) -> second
			));

		weekDates(command.weekStartDate()).forEach(recordDate -> {
			DevotionDailyCheckCommand dailyCommand = requestedChecks.getOrDefault(
				recordDate,
				new DevotionDailyCheckCommand(recordDate, false, false, false)
			);
			upsertDailyCheck(
				targetWeeklyRecord.id(),
				recordDate,
				dailyCommand.quietTimeChecked(),
				dailyCommand.prayerChecked(),
				dailyCommand.bibleReadingChecked()
			);
		});

		List<DevotionDailyCheck> dailyChecks = refreshWeeklySummary(targetWeeklyRecord, command.saturdayLateMinutes());
		if (command.submit()) {
			targetWeeklyRecord.submit(Instant.now());
			createPenaltyCharge(command, requester.userId(), targetWeeklyRecord);
		}
		return WeeklyDevotionResult.of(targetWeeklyRecord, getCampusOrThrow(command.campusId()), dailyChecks);
	}

	private void validateNotSubmitted(WeeklyDevotionRecord weeklyRecord) {
		if (weeklyRecord != null && weeklyRecord.submittedAt() != null) {
			throw new BusinessException(ErrorCode.DEVOTION_WEEKLY_ALREADY_SUBMITTED);
		}
	}

	private void createPenaltyCharge(
		UpdateWeeklyDevotionCommand command,
		Long userId,
		WeeklyDevotionRecord weeklyRecord
	) {
		List<PenaltyRule> activeRules = penaltyRuleRepository.findByCampusIdOrderByIdAsc(command.campusId())
			.stream()
			.filter(PenaltyRule::isActive)
			.toList();
		DevotionFineCalculationResult calculation = fineCalculator.calculate(new DevotionFineCalculationInput(
			weeklyRecord.quietTimeCount(),
			weeklyRecord.prayerCount(),
			weeklyRecord.bibleReadingCount(),
			weeklyRecord.saturdayLateMinutes()
		), activeRules);

		if (calculation.totalAmount() == 0) {
			return;
		}
		penaltyChargePort.createPenaltyCharge(new DevotionPenaltyChargeCommand(
			command.campusId(),
			userId,
			weeklyRecord.id(),
			PENALTY_CHARGE_TITLE,
			command.weekStartDate() + " 주간",
			calculation.totalAmount(),
			command.weekStartDate().plusDays(7)
		));
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

	private List<DevotionDailyCheck> refreshWeeklySummary(WeeklyDevotionRecord weeklyRecord, int saturdayLateMinutes) {
		List<DevotionDailyCheck> dailyChecks = dailyCheckRepository.findByWeeklyRecordIdOrderByRecordDateAsc(weeklyRecord.id());
		weeklyRecord.updateSummary(dailyChecks, saturdayLateMinutes);
		return dailyChecks;
	}

	private List<LocalDate> weekDates(LocalDate weekStartDate) {
		return LongStream.rangeClosed(0, 6)
			.mapToObj(weekStartDate::plusDays)
			.toList();
	}

	private void validateMonday(LocalDate weekStartDate) {
		if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new BusinessException(ErrorCode.DEVOTION_INVALID_WEEK_START_DATE);
		}
	}

	private void validateSaturdayLateMinutes(int saturdayLateMinutes) {
		if (saturdayLateMinutes < 0 || saturdayLateMinutes > MAX_SATURDAY_LATE_MINUTES) {
			throw new BusinessException(ErrorCode.DEVOTION_INVALID_SATURDAY_LATE_MINUTES);
		}
	}

	private void validateDailyChecksInWeek(LocalDate weekStartDate, List<DevotionDailyCheckCommand> dailyChecks) {
		LocalDate weekEndDate = weekStartDate.plusDays(6);
		boolean hasOutOfWeekDate = dailyChecks.stream()
			.map(DevotionDailyCheckCommand::recordDate)
			.anyMatch(recordDate -> recordDate.isBefore(weekStartDate) || recordDate.isAfter(weekEndDate));
		if (hasOutOfWeekDate) {
			throw new BusinessException(ErrorCode.DEVOTION_DAILY_CHECK_DATE_OUT_OF_WEEK);
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
