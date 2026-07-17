package com.faithlog.prayer.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.prayer.domain.entity.PrayerGroup;
import com.faithlog.prayer.domain.entity.PrayerGroupMember;
import com.faithlog.prayer.infrastructure.repository.PrayerGroupMemberRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerGroupRepository;
import com.faithlog.prayer.service.result.PrayerAssignableMemberResult;
import com.faithlog.prayer.service.result.PrayerGroupMemberResult;
import com.faithlog.prayer.service.result.PrayerGroupResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class PrayerTargetMemberSupport {

	private final PrayerGroupRepository groupRepository;
	private final PrayerGroupMemberRepository groupMemberRepository;
	private final PrayerAccessSupport accessSupport;

	PrayerTargetMemberSupport(
		PrayerGroupRepository groupRepository,
		PrayerGroupMemberRepository groupMemberRepository,
		PrayerAccessSupport accessSupport
	) {
		this.groupRepository = groupRepository;
		this.groupMemberRepository = groupMemberRepository;
		this.accessSupport = accessSupport;
	}

	PrayerGroupResult toGroupResult(PrayerGroup group) {
		List<PrayerGroupMemberResult> members = groupMemberRepository.findByGroupIdAndIsActiveTrueOrderByIdAsc(group.id())
			.stream()
			.map(member -> {
				CampusUserLookupResult user = accessSupport.getUserOrThrow(member.userId());
				return new PrayerGroupMemberResult(member.userId(), user.name(), user.email());
			})
			.toList();
		return PrayerGroupResult.of(group, members);
	}

	List<PrayerGroupResult> toGroupResults(List<PrayerGroup> groups) {
		if (groups.isEmpty()) {
			return List.of();
		}
		List<Long> groupIds = groups.stream().map(PrayerGroup::id).toList();
		List<PrayerGroupMember> members = groupMemberRepository.findByGroupIdInAndIsActiveTrueOrderByIdAsc(groupIds);
		Map<Long, CampusUserLookupResult> usersById = members.isEmpty()
			? Map.of()
			: accessSupport.campusUsersById(members.stream().map(PrayerGroupMember::userId).distinct().toList());
		Map<Long, List<PrayerGroupMemberResult>> resultsByGroupId = new HashMap<>();
		for (PrayerGroupMember member : members) {
			CampusUserLookupResult user = usersById.get(member.userId());
			resultsByGroupId.computeIfAbsent(member.groupId(), ignored -> new ArrayList<>())
				.add(new PrayerGroupMemberResult(member.userId(), user.name(), user.email()));
		}
		return groups.stream()
			.map(group -> PrayerGroupResult.of(group, resultsByGroupId.getOrDefault(group.id(), List.of())))
			.toList();
	}

	PrayerAssignableMemberResult toAssignableMember(
		Long userId,
		Map<Long, Long> assignedGroupIdByUserId,
		Map<Long, PrayerGroup> groupById
	) {
		CampusUserLookupResult user = accessSupport.getUserOrThrow(userId);
		Long assignedGroupId = assignedGroupIdByUserId.get(userId);
		PrayerGroup assignedGroup = assignedGroupId == null ? null : groupById.get(assignedGroupId);
		return new PrayerAssignableMemberResult(
			user.userId(),
			user.name(),
			user.email(),
			assignedGroupId,
			assignedGroup == null ? null : assignedGroup.name(),
			assignedGroupId == null
		);
	}

	TargetMembers loadTargetMembers(Long campusId, Long seasonId) {
		List<PrayerGroup> groups = groupRepository.findBySeasonIdAndIsActiveTrueOrderBySortOrderAscIdAsc(seasonId);
		List<Long> groupIds = groups.stream().map(PrayerGroup::id).toList();
		Map<Long, CampusMember> activeCampusMembers = accessSupport.activeCampusMemberMap(campusId);
		List<PrayerGroupMember> members = groupIds.isEmpty()
			? List.of()
			: groupMemberRepository.findByGroupIdInAndIsActiveTrueOrderByIdAsc(groupIds)
				.stream()
				.filter(member -> activeCampusMembers.containsKey(member.userId()))
				.toList();
		Map<Long, List<PrayerGroupMember>> membersByGroupId = new HashMap<>();
		Map<Long, Long> groupIdByUserId = new HashMap<>();
		for (PrayerGroupMember member : members) {
			membersByGroupId.computeIfAbsent(member.groupId(), ignored -> new ArrayList<>()).add(member);
			groupIdByUserId.put(member.userId(), member.groupId());
		}
		return new TargetMembers(groups, membersByGroupId, groupIdByUserId);
	}

	record TargetMembers(
		List<PrayerGroup> groups,
		Map<Long, List<PrayerGroupMember>> membersByGroupId,
		Map<Long, Long> groupIdByUserId
	) {

		Set<Long> userIds() {
			return groupIdByUserId.keySet();
		}
	}
}
