package com.faithlog.prayer.service;

import com.faithlog.prayer.service.command.ClosePrayerSeasonCommand;
import com.faithlog.prayer.service.command.CreatePrayerGroupCommand;
import com.faithlog.prayer.service.command.CreatePrayerSeasonCommand;
import com.faithlog.prayer.service.command.PrayerSubmissionCommand;
import com.faithlog.prayer.service.command.ReplacePrayerGroupMembersCommand;
import com.faithlog.prayer.service.command.SaveMyPrayerSubmissionCommand;
import com.faithlog.prayer.service.command.SavePrayerSubmissionsCommand;
import com.faithlog.prayer.service.command.UpdatePrayerGroupCommand;
import com.faithlog.prayer.service.result.PrayerAssignableMemberResult;
import com.faithlog.prayer.service.result.PrayerGroupBoardResult;
import com.faithlog.prayer.service.result.PrayerGroupMemberResult;
import com.faithlog.prayer.service.result.PrayerGroupResult;
import com.faithlog.prayer.service.result.PrayerMemberSubmissionResult;
import com.faithlog.prayer.service.result.PrayerSeasonResult;
import com.faithlog.prayer.service.result.PrayerWeekBoardResult;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.prayer.domain.entity.PrayerGroup;
import com.faithlog.prayer.domain.entity.PrayerGroupMember;
import com.faithlog.prayer.domain.entity.PrayerSeason;
import com.faithlog.prayer.domain.type.PrayerSeasonStatus;
import com.faithlog.prayer.domain.entity.PrayerSubmission;
import com.faithlog.prayer.domain.entity.PrayerWeek;
import com.faithlog.prayer.domain.type.PrayerWeekStatus;
import com.faithlog.prayer.infrastructure.repository.PrayerGroupMemberRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerGroupRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerSeasonRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerSubmissionRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerWeekRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrayerService {

	private final PrayerSeasonRepository seasonRepository;
	private final PrayerGroupRepository groupRepository;
	private final PrayerGroupMemberRepository groupMemberRepository;
	private final PrayerWeekRepository weekRepository;
	private final PrayerSubmissionRepository submissionRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;

	public PrayerService(
		PrayerSeasonRepository seasonRepository,
		PrayerGroupRepository groupRepository,
		PrayerGroupMemberRepository groupMemberRepository,
		PrayerWeekRepository weekRepository,
		PrayerSubmissionRepository submissionRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort
	) {
		this.seasonRepository = seasonRepository;
		this.groupRepository = groupRepository;
		this.groupMemberRepository = groupMemberRepository;
		this.weekRepository = weekRepository;
		this.submissionRepository = submissionRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
	}

	@Transactional
	public PrayerSeasonResult createSeason(CreatePrayerSeasonCommand command) {
		requirePrayerManager(command.campusId(), command.requesterId());
		if (seasonRepository.existsByCampusIdAndStatus(command.campusId(), PrayerSeasonStatus.ACTIVE)) {
			throw new BusinessException(ErrorCode.PRAYER_ACTIVE_SEASON_ALREADY_EXISTS);
		}
		PrayerSeason season = seasonRepository.save(PrayerSeason.create(
			command.campusId(),
			command.name(),
			command.startDate(),
			command.requesterId()
		));
		return PrayerSeasonResult.from(season);
	}

	@Transactional
	public PrayerSeasonResult closeSeason(ClosePrayerSeasonCommand command) {
		PrayerSeason season = getSeason(command.seasonId());
		requirePrayerManager(season.campusId(), command.requesterId());
		season.close(command.endDate());
		return PrayerSeasonResult.from(season);
	}

	@Transactional
	public PrayerGroupResult createGroup(CreatePrayerGroupCommand command) {
		PrayerSeason season = getSeason(command.seasonId());
		requirePrayerManager(season.campusId(), command.requesterId());
		PrayerGroup group = groupRepository.save(PrayerGroup.create(season.id(), command.name(), command.sortOrder()));
		return toGroupResult(group);
	}

	@Transactional
	public PrayerGroupResult updateGroup(UpdatePrayerGroupCommand command) {
		PrayerGroup group = getGroup(command.groupId());
		PrayerSeason season = getSeason(group.seasonId());
		requirePrayerManager(season.campusId(), command.requesterId());
		group.update(command.name(), command.sortOrder(), command.isActive());
		return toGroupResult(group);
	}

	@Transactional
	public PrayerGroupResult replaceGroupMembers(ReplacePrayerGroupMembersCommand command) {
		PrayerGroup group = getGroup(command.groupId());
		PrayerSeason season = getSeason(group.seasonId());
		requirePrayerManager(season.campusId(), command.requesterId());
		List<Long> requestedUserIds = distinctUserIds(command.userIds());
		Map<Long, CampusMember> activeCampusMembers = activeCampusMemberMap(season.campusId());
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
		return toGroupResult(group);
	}

	@Transactional(readOnly = true)
	public PrayerSeasonResult getCurrentSeason(Long campusId, Long requesterId) {
		requirePrayerManager(campusId, requesterId);
		return findCurrentSeason(campusId)
			.map(PrayerSeasonResult::from)
			.orElse(null);
	}

	@Transactional(readOnly = true)
	public List<PrayerGroupResult> getSeasonGroups(Long seasonId, Long requesterId) {
		PrayerSeason season = getSeason(seasonId);
		requirePrayerManager(season.campusId(), requesterId);
		return groupRepository.findBySeasonIdAndIsActiveTrueOrderBySortOrderAscIdAsc(season.id())
			.stream()
			.map(this::toGroupResult)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<PrayerAssignableMemberResult> getAssignableMembers(Long seasonId, Long requesterId) {
		PrayerSeason season = getSeason(seasonId);
		requirePrayerManager(season.campusId(), requesterId);
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
			.map(userId -> toAssignableMember(userId, assignedGroupIdByUserId, groupById))
			.toList();
	}

	@Transactional(readOnly = true)
	public PrayerWeekBoardResult getWeeklyBoard(Long campusId, LocalDate weekStartDate, Long requesterId) {
		validateWeekStartDate(weekStartDate);
		CampusUserLookupResult requester = requirePrayerReader(campusId, requesterId);
		PrayerSeason season = findCurrentSeason(campusId).orElse(null);
		if (season == null) {
			return PrayerWeekBoardResult.empty(campusId, weekStartDate);
		}
		PrayerWeek week = weekRepository.findByCampusIdAndSeasonIdAndWeekStartDate(campusId, season.id(), weekStartDate).orElse(null);
		List<PrayerSubmission> submissions = week == null ? List.of() : submissionRepository.findByPrayerWeekId(week.id());
		return buildBoard(campusId, season, weekStartDate, week, submissions, requester);
	}

	@Transactional
	public PrayerWeekBoardResult saveSubmissions(SavePrayerSubmissionsCommand command) {
		validateWeekStartDate(command.weekStartDate());
		CampusUserLookupResult requester = getActiveUser(command.requesterId(), ErrorCode.AUTH_UNAUTHORIZED);
		PrayerSeason season = getCurrentSeasonOrThrow(command.campusId());
		TargetMembers targetMembers = loadTargetMembers(command.campusId(), season.id());
		requireSubmissionPermission(command.campusId(), requester, command.submissions(), targetMembers);
		Map<Long, PrayerSubmissionCommand> requestedByUserId = requestedSubmissionMap(command.submissions());
		if (!targetMembers.userIds().containsAll(requestedByUserId.keySet())) {
			throw new BusinessException(ErrorCode.PRAYER_MEMBER_NOT_FOUND);
		}

		PrayerWeek existingWeek = weekRepository
			.findByCampusIdAndSeasonIdAndWeekStartDate(command.campusId(), season.id(), command.weekStartDate())
			.orElse(null);
		Map<Long, PrayerSubmission> existingByUserId = existingWeek == null
			? Map.of()
			: submissionRepository.findByPrayerWeekId(existingWeek.id()).stream()
				.collect(Collectors.toMap(PrayerSubmission::userId, Function.identity()));
		validateVersions(requestedByUserId.values(), existingByUserId);

		PrayerWeek week = existingWeek == null
			? weekRepository.save(PrayerWeek.create(command.campusId(), season.id(), command.weekStartDate()))
			: existingWeek;
		Instant now = Instant.now();
		for (PrayerSubmissionCommand submissionCommand : requestedByUserId.values()) {
			PrayerSubmission existing = existingByUserId.get(submissionCommand.userId());
			if (existing == null) {
				submissionRepository.save(PrayerSubmission.create(
					week.id(),
					targetMembers.groupIdByUserId().get(submissionCommand.userId()),
					submissionCommand.userId(),
					submissionCommand.content(),
					requester.userId(),
					now
				));
			} else {
				updateExistingSubmission(existing, submissionCommand, requester.userId(), now);
			}
		}
		List<PrayerSubmission> submissions = submissionRepository.findByPrayerWeekId(week.id());
		return buildBoard(command.campusId(), season, command.weekStartDate(), week, submissions, requester);
	}

	@Transactional
	public PrayerWeekBoardResult saveMySubmission(SaveMyPrayerSubmissionCommand command) {
		validateWeekStartDate(command.weekStartDate());
		CampusUserLookupResult requester = getActiveUser(command.requesterId(), ErrorCode.AUTH_UNAUTHORIZED);
		PrayerSeason season = getCurrentSeasonOrThrow(command.campusId());
		requirePrayerReader(command.campusId(), requester.userId());
		TargetMembers targetMembers = loadTargetMembers(command.campusId(), season.id());
		Long groupId = targetMembers.groupIdByUserId().get(requester.userId());
		if (groupId == null) {
			throw new BusinessException(ErrorCode.PRAYER_GROUP_ASSIGNMENT_REQUIRED);
		}

		PrayerWeek week = weekRepository
			.findByCampusIdAndSeasonIdAndWeekStartDate(command.campusId(), season.id(), command.weekStartDate())
			.orElseGet(() -> weekRepository.save(PrayerWeek.create(command.campusId(), season.id(), command.weekStartDate())));
		Instant now = Instant.now();
		PrayerSubmission existing = submissionRepository.findByPrayerWeekIdAndUserId(week.id(), requester.userId()).orElse(null);
		if (existing == null) {
			submissionRepository.save(PrayerSubmission.create(
				week.id(),
				groupId,
				requester.userId(),
				command.content(),
				requester.userId(),
				now
			));
		} else {
			submissionRepository.updateContent(existing.id(), command.content(), requester.userId(), now);
		}
		List<PrayerSubmission> submissions = submissionRepository.findByPrayerWeekId(week.id());
		return buildBoard(command.campusId(), season, command.weekStartDate(), week, submissions, requester);
	}

	private void updateExistingSubmission(
		PrayerSubmission existing,
		PrayerSubmissionCommand command,
		Long requesterId,
		Instant submittedAt
	) {
		int updatedRows = submissionRepository.updateContentIfVersionMatches(
			existing.id(),
			command.content(),
			requesterId,
			submittedAt,
			command.version()
		);
		if (updatedRows == 0) {
			throw new BusinessException(ErrorCode.PRAYER_SUBMISSION_CONFLICT);
		}
	}

	private PrayerGroupResult toGroupResult(PrayerGroup group) {
		List<PrayerGroupMemberResult> members = groupMemberRepository.findByGroupIdAndIsActiveTrueOrderByIdAsc(group.id())
			.stream()
			.map(member -> {
				CampusUserLookupResult user = getUserOrThrow(member.userId());
				return new PrayerGroupMemberResult(member.userId(), user.name(), user.email());
			})
			.toList();
		return PrayerGroupResult.of(group, members);
	}

	private PrayerWeekBoardResult buildBoard(
		Long campusId,
		PrayerSeason season,
		LocalDate weekStartDate,
		PrayerWeek week,
		List<PrayerSubmission> submissions,
		CampusUserLookupResult requester
	) {
		TargetMembers targetMembers = loadTargetMembers(campusId, season.id());
		Map<Long, PrayerSubmission> submissionsByUserId = submissions.stream()
			.collect(Collectors.toMap(PrayerSubmission::userId, Function.identity(), (left, right) -> left));
		Map<Long, CampusUserLookupResult> usersById = campusUsersById(targetMembers.userIds());
		Long myGroupId = targetMembers.groupIdByUserId().get(requester.userId());
		boolean canEditAll = requester.isAdmin() || isCampusManager(campusId, requester.userId());
		List<PrayerGroupBoardResult> groups = targetMembers.groups().stream()
			.map(group -> {
				List<PrayerMemberSubmissionResult> members = targetMembers.membersByGroupId().getOrDefault(group.id(), List.of())
					.stream()
					.map(member -> toMemberSubmission(
						member,
						usersById.get(member.userId()),
						submissionsByUserId.get(member.userId()),
						canEditAll,
						requester.userId(),
						myGroupId
					))
					.toList();
				return new PrayerGroupBoardResult(group.id(), group.seasonId(), group.name(), group.sortOrder(), members);
			})
			.toList();
		long targetMemberCount = groups.stream()
			.mapToLong(group -> group.members().size())
			.sum();
		long submittedCount = groups.stream()
			.flatMap(group -> group.members().stream())
			.filter(member -> member.submittedAt() != null)
			.count();
		return new PrayerWeekBoardResult(
			campusId,
			weekStartDate,
			weekStartDate.plusDays(6),
			PrayerSeasonResult.from(season),
			myGroupId,
			week == null ? PrayerWeekStatus.OPEN.name() : week.status().name(),
			submittedCount,
			targetMemberCount,
			groups
		);
	}

	private PrayerMemberSubmissionResult toMemberSubmission(
		PrayerGroupMember member,
		CampusUserLookupResult user,
		PrayerSubmission submission,
		boolean canEditAll,
		Long requesterId,
		Long requesterGroupId
	) {
		boolean editable = canEditAll || (member.userId().equals(requesterId) && member.groupId().equals(requesterGroupId));
		if (submission == null) {
			return new PrayerMemberSubmissionResult(user.userId(), user.name(), null, null, false, editable, 0, null);
		}
		return new PrayerMemberSubmissionResult(
			user.userId(),
			user.name(),
			submission.id(),
			submission.content(),
			submission.submittedAt() != null,
			editable,
			submission.version(),
			submission.submittedAt()
		);
	}

	private TargetMembers loadTargetMembers(Long campusId, Long seasonId) {
		List<PrayerGroup> groups = groupRepository.findBySeasonIdAndIsActiveTrueOrderBySortOrderAscIdAsc(seasonId);
		List<Long> groupIds = groups.stream().map(PrayerGroup::id).toList();
		Map<Long, CampusMember> activeCampusMembers = activeCampusMemberMap(campusId);
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

	private PrayerAssignableMemberResult toAssignableMember(
		Long userId,
		Map<Long, Long> assignedGroupIdByUserId,
		Map<Long, PrayerGroup> groupById
	) {
		CampusUserLookupResult user = getUserOrThrow(userId);
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

	private void requireSubmissionPermission(
		Long campusId,
		CampusUserLookupResult requester,
		List<PrayerSubmissionCommand> submissions,
		TargetMembers targetMembers
	) {
		if (requester.isAdmin() || isCampusManager(campusId, requester.userId())) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_SUBMISSION_FORBIDDEN));
		Long requesterGroupId = targetMembers.groupIdByUserId().get(requesterMembership.userId());
		if (requesterGroupId == null) {
			throw new BusinessException(ErrorCode.PRAYER_SUBMISSION_FORBIDDEN);
		}
		boolean allInRequesterGroup = submissions.stream()
			.allMatch(submission -> requesterGroupId.equals(targetMembers.groupIdByUserId().get(submission.userId())));
		if (!allInRequesterGroup) {
			throw new BusinessException(ErrorCode.PRAYER_SUBMISSION_FORBIDDEN);
		}
	}

	private void validateVersions(Collection<PrayerSubmissionCommand> requestedSubmissions, Map<Long, PrayerSubmission> existingByUserId) {
		for (PrayerSubmissionCommand command : requestedSubmissions) {
			PrayerSubmission existing = existingByUserId.get(command.userId());
			int currentVersion = existing == null ? 0 : existing.version();
			if (command.version() != currentVersion) {
				throw new BusinessException(ErrorCode.PRAYER_SUBMISSION_CONFLICT);
			}
		}
	}

	private Map<Long, PrayerSubmissionCommand> requestedSubmissionMap(List<PrayerSubmissionCommand> submissions) {
		if (submissions == null || submissions.isEmpty()) {
			throw new BusinessException(ErrorCode.PRAYER_INVALID_SUBMISSION_REQUEST);
		}
		Map<Long, PrayerSubmissionCommand> requestedByUserId = new LinkedHashMap<>();
		for (PrayerSubmissionCommand submission : submissions) {
			if (submission.userId() == null || submission.version() < 0 || requestedByUserId.put(submission.userId(), submission) != null) {
				throw new BusinessException(ErrorCode.PRAYER_INVALID_SUBMISSION_REQUEST);
			}
		}
		return requestedByUserId;
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

	private void validateWeekStartDate(LocalDate weekStartDate) {
		if (weekStartDate == null || weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new BusinessException(ErrorCode.PRAYER_INVALID_WEEK_START_DATE);
		}
	}

	private CampusUserLookupResult requirePrayerReader(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId, ErrorCode.AUTH_UNAUTHORIZED);
		if (requester.isAdmin()) {
			return requester;
		}
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_ACCESS_FORBIDDEN));
		return requester;
	}

	private void requirePrayerManager(Long campusId, Long requesterId) {
		if (!campusRepository.findById(campusId).filter(campus -> campus.isActive()).isPresent()) {
			throw new BusinessException(ErrorCode.CAMPUS_NOT_FOUND);
		}
		CampusUserLookupResult requester = getActiveUser(requesterId, ErrorCode.AUTH_UNAUTHORIZED);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_MANAGE_FORBIDDEN));
		if (!requesterMembership.canManageCampusMembers()) {
			throw new BusinessException(ErrorCode.PRAYER_MANAGE_FORBIDDEN);
		}
	}

	private boolean isCampusManager(Long campusId, Long userId) {
		return campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.filter(CampusMember::isActive)
			.map(CampusMember::canManageCampusMembers)
			.orElse(false);
	}

	private Map<Long, CampusMember> activeCampusMemberMap(Long campusId) {
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE)
			.stream()
			.collect(Collectors.toMap(CampusMember::userId, Function.identity()));
	}

	private java.util.Optional<PrayerSeason> findCurrentSeason(Long campusId) {
		return seasonRepository.findByCampusIdAndStatusAndEndDateIsNull(campusId, PrayerSeasonStatus.ACTIVE);
	}

	private PrayerSeason getCurrentSeasonOrThrow(Long campusId) {
		return findCurrentSeason(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_ACTIVE_SEASON_NOT_FOUND));
	}

	private PrayerSeason getSeason(Long seasonId) {
		return seasonRepository.findById(seasonId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_SEASON_NOT_FOUND));
	}

	private PrayerGroup getGroup(Long groupId) {
		return groupRepository.findById(groupId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_GROUP_NOT_FOUND));
	}

	private CampusUserLookupResult getActiveUser(Long userId, ErrorCode errorCode) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(errorCode));
		if (!user.active()) {
			throw new BusinessException(errorCode);
		}
		return user;
	}

	private CampusUserLookupResult getUserOrThrow(Long userId) {
		return userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_MEMBER_NOT_FOUND));
	}

	private Map<Long, CampusUserLookupResult> campusUsersById(Collection<Long> userIds) {
		Map<Long, CampusUserLookupResult> usersById = userLookupPort.findCampusUsersByIds(userIds)
			.stream()
			.collect(Collectors.toMap(CampusUserLookupResult::userId, Function.identity()));
		for (Long userId : userIds) {
			if (!usersById.containsKey(userId)) {
				throw new BusinessException(ErrorCode.PRAYER_MEMBER_NOT_FOUND);
			}
		}
		return usersById;
	}

	private record TargetMembers(
		List<PrayerGroup> groups,
		Map<Long, List<PrayerGroupMember>> membersByGroupId,
		Map<Long, Long> groupIdByUserId
	) {

		Set<Long> userIds() {
			return groupIdByUserId.keySet();
		}
	}
}
