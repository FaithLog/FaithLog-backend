package com.faithlog.prayer.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.prayer.domain.entity.PrayerGroup;
import com.faithlog.prayer.domain.entity.PrayerGroupMember;
import com.faithlog.prayer.domain.entity.PrayerSeason;
import com.faithlog.prayer.infrastructure.repository.PrayerGroupMemberRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerGroupRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerSeasonRepository;
import com.faithlog.prayer.service.result.PrayerAssignableMemberResult;
import com.faithlog.prayer.service.result.PrayerGroupResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrayerGroupQueryService {

	private final PrayerSeasonRepository seasonRepository;
	private final PrayerGroupRepository groupRepository;
	private final PrayerGroupMemberRepository groupMemberRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final PrayerAccessSupport accessSupport;
	private final PrayerTargetMemberSupport targetMemberSupport;

	public PrayerGroupQueryService(
		PrayerSeasonRepository seasonRepository,
		PrayerGroupRepository groupRepository,
		PrayerGroupMemberRepository groupMemberRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		PrayerAccessSupport accessSupport,
		PrayerTargetMemberSupport targetMemberSupport
	) {
		this.seasonRepository = seasonRepository;
		this.groupRepository = groupRepository;
		this.groupMemberRepository = groupMemberRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.accessSupport = accessSupport;
		this.targetMemberSupport = targetMemberSupport;
	}

	@Transactional(readOnly = true)
	public List<PrayerGroupResult> getSeasonGroups(Long seasonId, Long requesterId) {
		PrayerSeason season = getSeason(seasonId);
		accessSupport.requirePrayerManager(season.campusId(), requesterId);
		return groupRepository.findBySeasonIdAndIsActiveTrueOrderBySortOrderAscIdAsc(season.id())
			.stream()
			.map(targetMemberSupport::toGroupResult)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<PrayerAssignableMemberResult> getAssignableMembers(Long seasonId, Long requesterId) {
		PrayerSeason season = getSeason(seasonId);
		accessSupport.requirePrayerManager(season.campusId(), requesterId);
		List<PrayerGroup> groups = groupRepository.findBySeasonIdAndIsActiveTrueOrderBySortOrderAscIdAsc(season.id());
		Map<Long, PrayerGroup> groupById = groups.stream()
			.collect(Collectors.toMap(PrayerGroup::id, Function.identity()));
		Map<Long, Long> assignedGroupIdByUserId = new HashMap<>();
		if (!groups.isEmpty()) {
			List<Long> groupIds = groups.stream().map(PrayerGroup::id).toList();
			for (PrayerGroupMember member : groupMemberRepository.findByGroupIdInAndIsActiveTrueOrderByIdAsc(groupIds)) {
				assignedGroupIdByUserId.putIfAbsent(member.userId(), member.groupId());
			}
		}
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(season.campusId(), CampusMemberStatus.ACTIVE)
			.stream()
			.map(CampusMember::userId)
			.map(userId -> targetMemberSupport.toAssignableMember(userId, assignedGroupIdByUserId, groupById))
			.toList();
	}

	private PrayerSeason getSeason(Long seasonId) {
		return seasonRepository.findById(seasonId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_SEASON_NOT_FOUND));
	}
}
