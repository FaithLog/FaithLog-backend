package com.faithlog.prayer.application;

import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.application.port.CampusRepositoryPort;
import com.faithlog.campus.application.port.CampusUserLookupPort;
import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.prayer.domain.PrayerGroup;
import com.faithlog.prayer.domain.PrayerGroupMember;
import com.faithlog.prayer.domain.PrayerSeason;
import com.faithlog.prayer.domain.PrayerSeasonStatus;
import com.faithlog.prayer.domain.PrayerSubmission;
import com.faithlog.prayer.domain.PrayerWeek;
import com.faithlog.prayer.domain.PrayerWeekStatus;
import com.faithlog.prayer.infrastructure.jpa.PrayerGroupMemberRepository;
import com.faithlog.prayer.infrastructure.jpa.PrayerGroupRepository;
import com.faithlog.prayer.infrastructure.jpa.PrayerSeasonRepository;
import com.faithlog.prayer.infrastructure.jpa.PrayerSubmissionRepository;
import com.faithlog.prayer.infrastructure.jpa.PrayerWeekRepository;
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
	public PrayerWeekBoardResult getWeeklyBoard(Long campusId, LocalDate weekStartDate, Long requesterId) {
		validateWeekStartDate(weekStartDate);
		requirePrayerReader(campusId, requesterId);
		PrayerSeason season = getActiveSeason(campusId);
		PrayerWeek week = weekRepository.findByCampusIdAndSeasonIdAndWeekStartDate(campusId, season.id(), weekStartDate).orElse(null);
		List<PrayerSubmission> submissions = week == null ? List.of() : submissionRepository.findByPrayerWeekId(week.id());
		return buildBoard(campusId, season, weekStartDate, week, submissions);
	}

	@Transactional
	public PrayerWeekBoardResult saveSubmissions(SavePrayerSubmissionsCommand command) {
		validateWeekStartDate(command.weekStartDate());
		CampusUserLookupResult requester = getActiveUser(command.requesterId(), ErrorCode.AUTH_UNAUTHORIZED);
		PrayerSeason season = getActiveSeason(command.campusId());
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
		return buildBoard(command.campusId(), season, command.weekStartDate(), week, submissions);
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
			.map(member -> new PrayerGroupMemberResult(member.userId(), getUserOrThrow(member.userId()).name()))
			.toList();
		return PrayerGroupResult.of(group, members);
	}

	private PrayerWeekBoardResult buildBoard(
		Long campusId,
		PrayerSeason season,
		LocalDate weekStartDate,
		PrayerWeek week,
		List<PrayerSubmission> submissions
	) {
		TargetMembers targetMembers = loadTargetMembers(campusId, season.id());
		Map<Long, PrayerSubmission> submissionsByUserId = submissions.stream()
			.collect(Collectors.toMap(PrayerSubmission::userId, Function.identity(), (left, right) -> left));
		List<PrayerGroupBoardResult> groups = targetMembers.groups().stream()
			.map(group -> {
				List<PrayerMemberSubmissionResult> members = targetMembers.membersByGroupId().getOrDefault(group.id(), List.of())
					.stream()
					.map(member -> toMemberSubmission(member, submissionsByUserId.get(member.userId())))
					.toList();
				return new PrayerGroupBoardResult(group.id(), group.name(), group.sortOrder(), members);
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
			week == null ? PrayerWeekStatus.OPEN.name() : week.status().name(),
			submittedCount,
			targetMemberCount,
			groups
		);
	}

	private PrayerMemberSubmissionResult toMemberSubmission(PrayerGroupMember member, PrayerSubmission submission) {
		CampusUserLookupResult user = getUserOrThrow(member.userId());
		if (submission == null) {
			return new PrayerMemberSubmissionResult(user.userId(), user.name(), null, null, 0, null);
		}
		return new PrayerMemberSubmissionResult(
			user.userId(),
			user.name(),
			submission.id(),
			submission.content(),
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

	private void requirePrayerReader(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId, ErrorCode.AUTH_UNAUTHORIZED);
		if (requester.isAdmin()) {
			return;
		}
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.PRAYER_ACCESS_FORBIDDEN));
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

	private PrayerSeason getActiveSeason(Long campusId) {
		return seasonRepository.findByCampusIdAndStatus(campusId, PrayerSeasonStatus.ACTIVE)
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
