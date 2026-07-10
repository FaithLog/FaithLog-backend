package com.faithlog.prayer.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.prayer.domain.entity.PrayerGroup;
import com.faithlog.prayer.domain.entity.PrayerGroupMember;
import com.faithlog.prayer.domain.entity.PrayerSeason;
import com.faithlog.prayer.infrastructure.repository.PrayerGroupMemberRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerGroupRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerSeasonRepository;
import com.faithlog.prayer.service.command.CreatePrayerGroupCommand;
import com.faithlog.prayer.service.command.ReplacePrayerGroupMembersCommand;
import com.faithlog.prayer.service.command.UpdatePrayerGroupCommand;
import com.faithlog.prayer.service.result.PrayerGroupResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrayerGroupCommandService {

	private final PrayerSeasonRepository seasonRepository;
	private final PrayerGroupRepository groupRepository;
	private final PrayerGroupMemberRepository groupMemberRepository;
	private final PrayerAccessSupport accessSupport;
	private final PrayerTargetMemberSupport targetMemberSupport;

	public PrayerGroupCommandService(
		PrayerSeasonRepository seasonRepository,
		PrayerGroupRepository groupRepository,
		PrayerGroupMemberRepository groupMemberRepository,
		PrayerAccessSupport accessSupport,
		PrayerTargetMemberSupport targetMemberSupport
	) {
		this.seasonRepository = seasonRepository;
		this.groupRepository = groupRepository;
		this.groupMemberRepository = groupMemberRepository;
		this.accessSupport = accessSupport;
		this.targetMemberSupport = targetMemberSupport;
	}

	@Transactional
	public PrayerGroupResult createGroup(CreatePrayerGroupCommand command) {
		PrayerSeason season = getSeason(command.seasonId());
		accessSupport.requirePrayerManager(season.campusId(), command.requesterId());
		PrayerGroup group = groupRepository.save(PrayerGroup.create(season.id(), command.name(), command.sortOrder()));
		return targetMemberSupport.toGroupResult(group);
	}

	@Transactional
	public PrayerGroupResult updateGroup(UpdatePrayerGroupCommand command) {
		PrayerGroup group = getGroup(command.groupId());
		PrayerSeason season = getSeason(group.seasonId());
		accessSupport.requirePrayerManager(season.campusId(), command.requesterId());
		group.update(command.name(), command.sortOrder(), command.isActive());
		return targetMemberSupport.toGroupResult(group);
	}

	@Transactional
	public PrayerGroupResult replaceGroupMembers(ReplacePrayerGroupMembersCommand command) {
		PrayerGroup group = getGroup(command.groupId());
		PrayerSeason season = getSeason(group.seasonId());
		accessSupport.requirePrayerManager(season.campusId(), command.requesterId());
		List<Long> requestedUserIds = distinctUserIds(command.userIds());
		Map<Long, CampusMember> activeCampusMembers = accessSupport.activeCampusMemberMap(season.campusId());
		for (Long userId : requestedUserIds) {
			if (!activeCampusMembers.containsKey(userId)) {
				throw new BusinessException(ErrorCode.PRAYER_MEMBER_NOT_FOUND);
			}
		}
		validateNoOtherActiveGroupAssignment(season.id(), group.id(), requestedUserIds);

		Map<Long, PrayerGroupMember> existingMembers = groupMemberRepository.findByGroupIdOrderByIdAsc(group.id())
			.stream()
			.collect(Collectors.toMap(PrayerGroupMember::userId, Function.identity()));
		Set<Long> requestedSet = new HashSet<>(requestedUserIds);
		for (PrayerGroupMember member : existingMembers.values()) {
			if (requestedSet.contains(member.userId())) {
				member.reactivate();
			} else {
				member.deactivate();
			}
		}
		for (Long userId : requestedUserIds) {
			if (!existingMembers.containsKey(userId)) {
				groupMemberRepository.save(PrayerGroupMember.create(group.id(), userId));
			}
		}
		return targetMemberSupport.toGroupResult(group);
	}

	private void validateNoOtherActiveGroupAssignment(Long seasonId, Long currentGroupId, List<Long> requestedUserIds) {
		List<Long> otherActiveGroupIds = groupRepository.findBySeasonIdAndIsActiveTrueOrderBySortOrderAscIdAsc(seasonId)
			.stream()
			.map(PrayerGroup::id)
			.filter(groupId -> !groupId.equals(currentGroupId))
			.toList();
		if (otherActiveGroupIds.isEmpty()) {
			return;
		}
		for (Long userId : requestedUserIds) {
			if (groupMemberRepository.existsByGroupIdInAndUserIdAndIsActiveTrue(otherActiveGroupIds, userId)) {
				throw new BusinessException(ErrorCode.PRAYER_GROUP_MEMBER_ALREADY_ASSIGNED);
			}
		}
	}

	private List<Long> distinctUserIds(List<Long> userIds) {
		if (userIds == null) {
			throw new BusinessException(ErrorCode.PRAYER_INVALID_SUBMISSION_REQUEST);
		}
		List<Long> distinct = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (Long userId : userIds) {
			if (userId == null || !seen.add(userId)) {
				throw new BusinessException(ErrorCode.PRAYER_INVALID_SUBMISSION_REQUEST);
			}
			distinct.add(userId);
		}
		return distinct;
	}

	private PrayerSeason getSeason(Long seasonId) {
		return seasonRepository.findById(seasonId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_SEASON_NOT_FOUND));
	}

	private PrayerGroup getGroup(Long groupId) {
		return groupRepository.findById(groupId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_GROUP_NOT_FOUND));
	}
}
