package com.faithlog.devotion.service;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.policy.CampusRolePolicy;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.devotion.service.query.GetAdminWeeklyDevotionMembersQuery;
import com.faithlog.devotion.service.result.AdminWeeklyDevotionResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminWeeklyDevotionQueryService {

	private static final Set<ChargeStatus> TOTAL_STATUSES = Set.of(ChargeStatus.UNPAID, ChargeStatus.PAID);

	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;
	private final WeeklyDevotionRecordRepository weeklyRecordRepository;
	private final DevotionDailyCheckRepository dailyCheckRepository;
	private final ChargeItemRepository chargeItemRepository;

	public AdminWeeklyDevotionQueryService(
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort,
		WeeklyDevotionRecordRepository weeklyRecordRepository,
		DevotionDailyCheckRepository dailyCheckRepository,
		ChargeItemRepository chargeItemRepository
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
		this.weeklyRecordRepository = weeklyRecordRepository;
		this.dailyCheckRepository = dailyCheckRepository;
		this.chargeItemRepository = chargeItemRepository;
	}

	@Transactional(readOnly = true)
	public AdminWeeklyDevotionResult getWeeklyMembers(GetAdminWeeklyDevotionMembersQuery query) {
		validateMonday(query.weekStartDate());
		CampusUserLookupResult requester = getActiveUser(query.requesterId());
		requireAccess(query.campusId(), requester);
		campusRepository.findById(query.campusId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));

		List<CampusMember> activeMembers = campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(
			query.campusId(),
			CampusMemberStatus.ACTIVE
		);
		Map<Long, CampusUserLookupResult> usersById = usersById(activeMembers);
		Map<Long, WeeklyDevotionRecord> weeklyRecordsByUserId = weeklyRecordRepository
			.findByCampusIdAndWeekStartDate(query.campusId(), query.weekStartDate())
			.stream()
			.collect(Collectors.toMap(WeeklyDevotionRecord::userId, Function.identity()));
		List<WeeklyDevotionRecord> submittedRecords = activeMembers.stream()
			.map(member -> weeklyRecordsByUserId.get(member.userId()))
			.filter(record -> record != null && record.submittedAt() != null)
			.toList();
		List<Long> submittedRecordIds = submittedRecords.stream().map(WeeklyDevotionRecord::id).toList();
		Map<Long, List<DevotionDailyCheck>> dailyChecksByRecordId = dailyChecksByRecordId(
			submittedRecordIds,
			query.weekStartDate()
		);
		Map<Long, ChargeItem> chargesBySourceId = chargesBySourceId(query.campusId(), submittedRecordIds);

		List<AdminWeeklyDevotionResult.SubmittedMember> submittedMembers = activeMembers.stream()
			.map(member -> toSubmittedMember(
				member,
				usersById,
				weeklyRecordsByUserId,
				dailyChecksByRecordId,
				chargesBySourceId,
				query.weekStartDate()
			))
			.flatMap(java.util.Optional::stream)
			.toList();
		List<AdminWeeklyDevotionResult.MissingMember> missingMembers = activeMembers.stream()
			.filter(member -> {
				WeeklyDevotionRecord record = weeklyRecordsByUserId.get(member.userId());
				return record == null || record.submittedAt() == null;
			})
			.map(member -> {
				CampusUserLookupResult user = requireUser(usersById, member.userId());
				return new AdminWeeklyDevotionResult.MissingMember(user.userId(), user.name(), user.email());
			})
			.toList();
		long totalPenaltyAmount = chargesBySourceId.values().stream()
			.filter(charge -> TOTAL_STATUSES.contains(charge.status()))
			.mapToLong(ChargeItem::amount)
			.sum();
		return new AdminWeeklyDevotionResult(
			query.weekStartDate(),
			query.weekStartDate().plusDays(6),
			activeMembers.size(),
			submittedMembers.size(),
			missingMembers.size(),
			totalPenaltyAmount,
			submittedMembers,
			missingMembers
		);
	}

	private java.util.Optional<AdminWeeklyDevotionResult.SubmittedMember> toSubmittedMember(
		CampusMember member,
		Map<Long, CampusUserLookupResult> usersById,
		Map<Long, WeeklyDevotionRecord> weeklyRecordsByUserId,
		Map<Long, List<DevotionDailyCheck>> dailyChecksByRecordId,
		Map<Long, ChargeItem> chargesBySourceId,
		LocalDate weekStartDate
	) {
		WeeklyDevotionRecord record = weeklyRecordsByUserId.get(member.userId());
		if (record == null || record.submittedAt() == null) {
			return java.util.Optional.empty();
		}
		CampusUserLookupResult user = requireUser(usersById, member.userId());
		ChargeItem charge = chargesBySourceId.get(record.id());
		AdminWeeklyDevotionResult.Penalty penalty = charge == null
			? null
			: new AdminWeeklyDevotionResult.Penalty(charge.id(), charge.amount(), charge.status());
		return java.util.Optional.of(new AdminWeeklyDevotionResult.SubmittedMember(
			user.userId(),
			user.name(),
			user.email(),
			record.quietTimeCount(),
			record.bibleReadingCount(),
			record.prayerCount(),
			record.saturdayLateMinutes(),
			record.submittedAt(),
			penalty,
			completeDailyChecks(dailyChecksByRecordId.getOrDefault(record.id(), List.of()), weekStartDate)
		));
	}

	private List<AdminWeeklyDevotionResult.DailyCheck> completeDailyChecks(
		List<DevotionDailyCheck> storedChecks,
		LocalDate weekStartDate
	) {
		Map<LocalDate, DevotionDailyCheck> checksByDate = storedChecks.stream()
			.collect(Collectors.toMap(DevotionDailyCheck::recordDate, Function.identity()));
		return IntStream.range(0, 7)
			.mapToObj(dayOffset -> {
				LocalDate recordDate = weekStartDate.plusDays(dayOffset);
				DevotionDailyCheck check = checksByDate.get(recordDate);
				return check == null
					? new AdminWeeklyDevotionResult.DailyCheck(null, recordDate, false, false, false)
					: new AdminWeeklyDevotionResult.DailyCheck(
						check.id(),
						check.recordDate(),
						check.quietTimeChecked(),
						check.bibleReadingChecked(),
						check.prayerChecked()
					);
			})
			.toList();
	}

	private Map<Long, List<DevotionDailyCheck>> dailyChecksByRecordId(
		List<Long> weeklyRecordIds,
		LocalDate weekStartDate
	) {
		if (weeklyRecordIds.isEmpty()) {
			return Map.of();
		}
		return dailyCheckRepository.findByWeeklyRecordIdInAndRecordDateBetweenOrderByRecordDateAsc(
			weeklyRecordIds,
			weekStartDate,
			weekStartDate.plusDays(6)
		).stream().collect(Collectors.groupingBy(
			DevotionDailyCheck::weeklyRecordId,
			LinkedHashMap::new,
			Collectors.toList()
		));
	}

	private Map<Long, ChargeItem> chargesBySourceId(Long campusId, List<Long> weeklyRecordIds) {
		if (weeklyRecordIds.isEmpty()) {
			return Map.of();
		}
		return chargeItemRepository
			.findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInOrderByIdAsc(
				campusId,
				PaymentCategory.PENALTY,
				ChargeSourceType.DEVOTION_RECORD,
				weeklyRecordIds
			)
			.stream()
			.collect(Collectors.toMap(ChargeItem::sourceId, Function.identity()));
	}

	private Map<Long, CampusUserLookupResult> usersById(List<CampusMember> activeMembers) {
		List<Long> userIds = activeMembers.stream().map(CampusMember::userId).toList();
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return userLookupPort.findCampusUsersByIds(userIds).stream()
			.collect(Collectors.toMap(CampusUserLookupResult::userId, Function.identity()));
	}

	private CampusUserLookupResult requireUser(Map<Long, CampusUserLookupResult> usersById, Long userId) {
		CampusUserLookupResult user = usersById.get(userId);
		if (user == null) {
			throw new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND);
		}
		return user;
	}

	private void requireAccess(Long campusId, CampusUserLookupResult requester) {
		if (requester.isAdmin()) {
			return;
		}
		CampusMember membership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVOTION_ADMIN_FORBIDDEN));
		CampusRolePolicy.requireCampusManager(membership, ErrorCode.DEVOTION_ADMIN_FORBIDDEN);
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}

	private void validateMonday(LocalDate weekStartDate) {
		if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new BusinessException(ErrorCode.DEVOTION_INVALID_WEEK_START_DATE);
		}
	}
}
