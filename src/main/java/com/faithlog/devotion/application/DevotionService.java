package com.faithlog.devotion.application;

import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.policy.CampusRolePolicy;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.devotion.domain.DevotionDailyCheck;
import com.faithlog.devotion.domain.DevotionFineCalculationInput;
import com.faithlog.devotion.domain.DevotionFineCalculationResult;
import com.faithlog.devotion.domain.DevotionFineCalculator;
import com.faithlog.devotion.domain.PenaltyRule;
import com.faithlog.devotion.domain.WeeklyDevotionRecord;
import com.faithlog.devotion.application.port.DevotionPenaltyChargeCommand;
import com.faithlog.devotion.application.port.DevotionPenaltyChargePort;
import com.faithlog.devotion.infrastructure.jpa.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.jpa.PenaltyRuleRepository;
import com.faithlog.devotion.infrastructure.jpa.WeeklyDevotionRecordRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevotionService {

	private static final String PENALTY_CHARGE_TITLE = "경건생활 벌금";

	private final WeeklyDevotionRecordRepository weeklyRecordRepository;
	private final DevotionDailyCheckRepository dailyCheckRepository;
	private final PenaltyRuleRepository penaltyRuleRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final DevotionFineCalculator fineCalculator;
	private final DevotionPenaltyChargePort penaltyChargePort;

	public DevotionService(
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

	@Transactional
	public WeeklyDevotionResult updateWeeklyCheck(UpdateWeeklyDevotionCommand command) {
		validateMonday(command.weekStartDate());
		validateSaturdayLateMinutes(command.saturdayLateMinutes());
		validateDailyChecksInWeek(command.weekStartDate(), command.dailyChecks());
		CampusUserLookupResult requester = getActiveUser(command.requesterId());
		requireActiveCampusMember(command.campusId(), requester.userId());
		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(command.campusId(), requester.userId(), command.weekStartDate())
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

	@Transactional(readOnly = true)
	public WeeklyDevotionResult getMyWeeklyCheck(GetMyWeeklyDevotionQuery query) {
		validateMonday(query.weekStartDate());
		CampusUserLookupResult requester = getActiveUser(query.requesterId());
		requireActiveCampusMember(query.campusId(), requester.userId());
		Campus campus = getCampusOrThrow(query.campusId());
		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(query.campusId(), requester.userId(), query.weekStartDate())
			.orElse(null);
		if (weeklyRecord == null) {
			return WeeklyDevotionResult.defaultOf(campus, requester.userId(), query.weekStartDate());
		}
		return WeeklyDevotionResult.of(
			weeklyRecord,
			campus,
			dailyCheckRepository.findByWeeklyRecordIdOrderByRecordDateAsc(weeklyRecord.id())
		);
	}

	@Transactional(readOnly = true)
	public List<MissingDevotionMemberResult> getMissingMembers(GetMissingDevotionMembersQuery query) {
		validateMonday(query.weekStartDate());
		CampusUserLookupResult requester = getActiveUser(query.requesterId());
		if (!requester.isAdmin()) {
			CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(query.campusId(), requester.userId())
				.filter(CampusMember::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.DEVOTION_ADMIN_FORBIDDEN));
			CampusRolePolicy.requireCampusManager(
				requesterMembership,
				ErrorCode.DEVOTION_ADMIN_FORBIDDEN,
				ErrorCode.DEVOTION_ADMIN_FORBIDDEN.message()
			);
		}

		Campus campus = getCampusOrThrow(query.campusId());
		Map<Long, WeeklyDevotionRecord> weeklyRecordsByUserId = weeklyRecordRepository
			.findByCampusIdAndWeekStartDate(query.campusId(), query.weekStartDate())
			.stream()
			.collect(Collectors.toMap(WeeklyDevotionRecord::userId, Function.identity()));
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(query.campusId(), CampusMemberStatus.ACTIVE)
			.stream()
			.filter(member -> {
				WeeklyDevotionRecord weeklyRecord = weeklyRecordsByUserId.get(member.userId());
				return weeklyRecord == null || weeklyRecord.submittedAt() == null;
			})
			.map(member -> MissingDevotionMemberResult.of(member, getUserOrThrow(member.userId()), campus))
			.toList();
	}

	private WeeklyDevotionRecord getOrCreateWeeklyRecord(Long campusId, Long userId, LocalDate weekStartDate) {
		return weeklyRecordRepository.findByCampusIdAndUserIdAndWeekStartDate(campusId, userId, weekStartDate)
			.orElseGet(() -> weeklyRecordRepository.save(WeeklyDevotionRecord.create(campusId, userId, weekStartDate)));
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

	private List<DevotionDailyCheck> refreshWeeklySummary(WeeklyDevotionRecord weeklyRecord) {
		return refreshWeeklySummary(weeklyRecord, weeklyRecord.saturdayLateMinutes());
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
		if (saturdayLateMinutes < 0) {
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

	private CampusUserLookupResult getUserOrThrow(Long userId) {
		return userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));
	}

	private Campus getCampusOrThrow(Long campusId) {
		return campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}
}
