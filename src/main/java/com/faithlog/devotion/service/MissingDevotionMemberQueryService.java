package com.faithlog.devotion.service;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.policy.CampusRolePolicy;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.devotion.service.query.GetMissingDevotionMembersQuery;
import com.faithlog.devotion.service.result.MissingDevotionMemberResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissingDevotionMemberQueryService {

	private final WeeklyDevotionRecordRepository weeklyRecordRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;

	public MissingDevotionMemberQueryService(
		WeeklyDevotionRecordRepository weeklyRecordRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort
	) {
		this.weeklyRecordRepository = weeklyRecordRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
	}

	@Transactional(readOnly = true)
	public List<MissingDevotionMemberResult> getMissingMembers(GetMissingDevotionMembersQuery query) {
		validateMonday(query.weekStartDate());
		CampusUserLookupResult requester = getActiveUser(query.requesterId());
		if (!requester.isAdmin()) {
			CampusMember requesterMembership = campusMemberRepository
				.findByCampusIdAndUserId(query.campusId(), requester.userId())
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

	private void validateMonday(LocalDate weekStartDate) {
		if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new BusinessException(ErrorCode.DEVOTION_INVALID_WEEK_START_DATE);
		}
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
