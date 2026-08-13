package com.faithlog.shepherd.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.shepherd.domain.entity.ShepherdGroup;
import com.faithlog.shepherd.domain.entity.ShepherdGroupAssignee;
import com.faithlog.shepherd.domain.entity.WeeklyShepherdAttendanceReport;
import com.faithlog.shepherd.domain.type.ShepherdGroupStatus;
import com.faithlog.shepherd.domain.type.WeeklyShepherdAttendanceStatus;
import com.faithlog.shepherd.infrastructure.repository.ShepherdAccessRepository;
import com.faithlog.shepherd.infrastructure.repository.ShepherdGroupAssigneeRepository;
import com.faithlog.shepherd.infrastructure.repository.ShepherdGroupRepository;
import com.faithlog.shepherd.infrastructure.repository.WeeklyShepherdAttendanceReportRepository;
import com.faithlog.shepherd.service.command.CreateShepherdGroupCommand;
import com.faithlog.shepherd.service.command.ReplaceShepherdGroupAssigneesCommand;
import com.faithlog.shepherd.service.command.SaveShepherdAttendanceCommand;
import com.faithlog.shepherd.service.command.UpdateShepherdGroupCommand;
import com.faithlog.shepherd.service.result.ShepherdAttendanceBoardGroupResult;
import com.faithlog.shepherd.service.result.ShepherdAttendanceBoardGroupRow;
import com.faithlog.shepherd.service.result.ShepherdAttendanceBoardResult;
import com.faithlog.shepherd.service.result.ShepherdAttendanceReportRow;
import com.faithlog.shepherd.service.result.ShepherdAttendanceReportResult;
import com.faithlog.shepherd.service.result.ShepherdAttendanceSummaryRow;
import com.faithlog.shepherd.service.result.ShepherdGroupAssigneeResult;
import com.faithlog.shepherd.service.result.ShepherdGroupAssigneeRow;
import com.faithlog.shepherd.service.result.ShepherdGroupResult;
import com.faithlog.shepherd.service.result.ShepherdGroupRow;
import com.faithlog.shepherd.service.result.ShepherdHomeCardResult;
import com.faithlog.shepherd.service.result.ShepherdHomeGroupResult;
import com.faithlog.shepherd.service.result.ShepherdHomeGroupRow;
import com.faithlog.shepherd.service.result.ShepherdHomeReportResult;
import com.faithlog.shepherd.service.result.ShepherdRequesterAccessRow;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShepherdService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_NOTE_LENGTH = 500;
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final ShepherdGroupRepository groupRepository;
	private final ShepherdGroupAssigneeRepository assigneeRepository;
	private final WeeklyShepherdAttendanceReportRepository reportRepository;
	private final ShepherdAccessRepository accessRepository;
	private final CampusMemberRepository campusMemberRepository;
	private final Clock clock;

	public ShepherdService(
		ShepherdGroupRepository groupRepository,
		ShepherdGroupAssigneeRepository assigneeRepository,
		WeeklyShepherdAttendanceReportRepository reportRepository,
		ShepherdAccessRepository accessRepository,
		CampusMemberRepository campusMemberRepository,
		Clock clock
	) {
		this.groupRepository = groupRepository;
		this.assigneeRepository = assigneeRepository;
		this.reportRepository = reportRepository;
		this.accessRepository = accessRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.clock = clock;
	}

	@Transactional
	public ShepherdGroupResult createGroup(CreateShepherdGroupCommand command) {
		ShepherdRequesterAccessRow requester = requireActiveRequester(command.campusId(), command.requesterId());
		requireActiveMembershipOrAdmin(requester, ErrorCode.SHEPHERD_GROUP_MANAGE_FORBIDDEN);
		String displayName = normalizeDisplayName(command.name());
		String normalizedName = normalizeLookupName(displayName);
		if (groupRepository.existsByCampusIdAndNormalizedName(command.campusId(), normalizedName)) {
			throw new BusinessException(ErrorCode.SHEPHERD_GROUP_DUPLICATE);
		}

		List<Long> assigneeUserIds = requester.isCampusManager() || requester.isServiceAdmin()
			? distinctAssigneeIds(command.assigneeUserIds(), true)
			: List.of(requester.userId());
		if (!requester.isCampusManager() && !requester.isServiceAdmin()
			&& command.assigneeUserIds() != null && !command.assigneeUserIds().isEmpty()) {
			assigneeUserIds = List.of(requester.userId());
		}
		validateActiveSameCampusMembers(command.campusId(), assigneeUserIds);

		try {
			ShepherdGroup group = groupRepository.save(ShepherdGroup.create(
				command.campusId(), displayName, normalizedName, requester.userId()));
			saveAssignees(group.id(), command.campusId(), assigneeUserIds, requester.userId());
			return toGroupResult(group);
		} catch (DataIntegrityViolationException exception) {
			if (isShepherdGroupNameUniqueViolation(exception)) {
				throw new BusinessException(ErrorCode.SHEPHERD_GROUP_DUPLICATE);
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public List<ShepherdGroupResult> getMyGroups(Long campusId, Long requesterId) {
		ShepherdRequesterAccessRow requester = requireActiveRequester(campusId, requesterId);
		requireActiveMembershipOrAdmin(requester, ErrorCode.SHEPHERD_GROUP_ACCESS_FORBIDDEN);
		List<ShepherdGroupRow> rows = requester.isServiceAdmin() || requester.isCampusManager()
			? groupRepository.findAdminGroupRows(campusId)
			: groupRepository.findMyGroupRows(campusId, requester.userId());
		return toGroupResults(rows);
	}

	@Transactional(readOnly = true)
	public List<ShepherdGroupResult> getAdminGroups(Long campusId, Long requesterId) {
		ShepherdRequesterAccessRow requester = requireActiveRequester(campusId, requesterId);
		requireCampusManagerOrAdmin(requester);
		return toGroupResults(groupRepository.findAdminGroupRows(campusId));
	}

	@Transactional(readOnly = true)
	public ShepherdHomeCardResult getMyHome(Long campusId, Long requesterId) {
		ShepherdRequesterAccessRow requester = requireActiveRequester(campusId, requesterId);
		requireActiveMembershipOrAdmin(requester, ErrorCode.SHEPHERD_GROUP_ACCESS_FORBIDDEN);
		LocalDate serviceDate = LocalDate.now(clock.withZone(SEOUL_ZONE));
		if (serviceDate.getDayOfWeek() != DayOfWeek.SUNDAY) {
			return ShepherdHomeCardResult.hidden();
		}
		List<ShepherdHomeGroupResult> groups = groupRepository
			.findMyHomeRows(campusId, requester.userId(), serviceDate)
			.stream()
			.map(this::toHomeGroupResult)
			.toList();
		if (groups.isEmpty()) {
			return ShepherdHomeCardResult.hidden();
		}
		return ShepherdHomeCardResult.visible(serviceDate, groups);
	}

	@Transactional
	public ShepherdGroupResult updateGroup(UpdateShepherdGroupCommand command) {
		ShepherdRequesterAccessRow requester = requireActiveRequester(command.campusId(), command.requesterId());
		requireCampusManagerOrAdmin(requester);
		ShepherdGroup group = groupRepository.findActiveByCampusIdAndIdForUpdate(command.campusId(), command.groupId())
			.orElseThrow(() -> new BusinessException(ErrorCode.SHEPHERD_GROUP_NOT_FOUND));
		if (command.version() == null || command.version() != group.version()) {
			throw new BusinessException(ErrorCode.SHEPHERD_ATTENDANCE_CONFLICT);
		}
		String displayName = normalizeDisplayName(command.name());
		String normalizedName = normalizeLookupName(displayName);
		if (!group.normalizedName().equals(normalizedName)
			&& groupRepository.existsByCampusIdAndNormalizedName(command.campusId(), normalizedName)) {
			throw new BusinessException(ErrorCode.SHEPHERD_GROUP_DUPLICATE);
		}
		try {
			group.update(displayName, normalizedName);
			groupRepository.flush();
			return toGroupResult(group);
		} catch (DataIntegrityViolationException exception) {
			if (isShepherdGroupNameUniqueViolation(exception)) {
				throw new BusinessException(ErrorCode.SHEPHERD_GROUP_DUPLICATE);
			}
			throw exception;
		}
	}

	@Transactional
	public ShepherdGroupResult replaceAssignees(ReplaceShepherdGroupAssigneesCommand command) {
		ShepherdRequesterAccessRow requester = requireActiveRequester(command.campusId(), command.requesterId());
		requireCampusManagerOrAdmin(requester);
		ShepherdGroup group = groupRepository.findActiveByCampusIdAndIdForUpdate(command.campusId(), command.groupId())
			.orElseThrow(() -> new BusinessException(ErrorCode.SHEPHERD_GROUP_NOT_FOUND));
		List<Long> requestedUserIds = distinctAssigneeIds(command.assigneeUserIds(), true);
		validateActiveSameCampusMembers(command.campusId(), requestedUserIds);

		Map<Long, ShepherdGroupAssignee> existing = assigneeRepository
			.findByCampusIdAndShepherdGroupIdOrderByUserIdAsc(command.campusId(), group.id())
			.stream()
			.collect(Collectors.toMap(ShepherdGroupAssignee::userId, Function.identity()));
		Set<Long> requested = new LinkedHashSet<>(requestedUserIds);
		List<Long> removals = existing.keySet().stream()
			.filter(userId -> !requested.contains(userId))
			.toList();
		if (!removals.isEmpty()) {
			assigneeRepository.deleteByCampusIdAndShepherdGroupIdAndUserIdIn(command.campusId(), group.id(), removals);
		}
		for (Long userId : requestedUserIds) {
			if (!existing.containsKey(userId)) {
				assigneeRepository.save(ShepherdGroupAssignee.create(group.id(), command.campusId(), userId, requester.userId()));
			}
		}
		return toGroupResult(group);
	}

	@Transactional(readOnly = true)
	public ShepherdAttendanceReportResult getAttendance(Long campusId, Long groupId, LocalDate serviceDate, Long requesterId) {
		validateServiceDate(serviceDate);
		ShepherdRequesterAccessRow requester = requireActiveRequester(campusId, requesterId);
		requireGroupReadable(campusId, groupId, requester);
		return reportRepository.findReportRowBySlot(campusId, groupId, serviceDate)
			.map(this::toReportResult)
			.orElse(null);
	}

	@Transactional
	public ShepherdAttendanceReportResult saveAttendance(SaveShepherdAttendanceCommand command) {
		validateServiceDate(command.serviceDate());
		validateCounts(command.smallGroupMeetingCount(), command.holyWaveCount(), command.otherWorshipCount());
		WeeklyShepherdAttendanceStatus status = parseStatus(command.status());
		String note = normalizeNote(command.note());
		int expectedVersion = command.version() == null ? -1 : command.version();
		if (expectedVersion < 0) {
			throw new BusinessException(ErrorCode.SHEPHERD_ATTENDANCE_CONFLICT);
		}
		ShepherdRequesterAccessRow requester = requireActiveRequester(command.campusId(), command.requesterId());
		ShepherdGroup group = requireGroupWritable(command.campusId(), command.groupId(), requester);
		WeeklyShepherdAttendanceReport existing = reportRepository
			.findBySlotForUpdate(command.campusId(), group.id(), command.serviceDate())
			.orElse(null);
		if (existing == null && expectedVersion != 0) {
			throw new BusinessException(ErrorCode.SHEPHERD_ATTENDANCE_CONFLICT);
		}
		if (existing != null && expectedVersion != existing.version()) {
			throw new BusinessException(ErrorCode.SHEPHERD_ATTENDANCE_CONFLICT);
		}
		Instant now = clock.instant();
		WeeklyShepherdAttendanceReport report = existing == null
			? reportRepository.save(WeeklyShepherdAttendanceReport.create(
				command.campusId(),
				group.id(),
				command.serviceDate(),
				command.smallGroupMeetingCount(),
				command.holyWaveCount(),
				command.otherWorshipCount(),
				note,
				status,
				requester.userId(),
				now
			))
			: existing;
		if (existing != null) {
			existing.update(
				command.smallGroupMeetingCount(),
				command.holyWaveCount(),
				command.otherWorshipCount(),
				note,
				status,
				requester.userId(),
				now
			);
		}
		return toReportResult(report, requester.name());
	}

	@Transactional(readOnly = true)
	public ShepherdAttendanceBoardResult getAdminAttendanceBoard(
		Long campusId,
		LocalDate serviceDate,
		Long requesterId,
		int page,
		int size
	) {
		validatePageRequest(page, size);
		return getAdminAttendanceBoard(campusId, serviceDate, requesterId, PageRequest.of(page, size));
	}

	@Transactional(readOnly = true)
	public ShepherdAttendanceBoardResult getAdminAttendanceBoard(
		Long campusId,
		LocalDate serviceDate,
		Long requesterId,
		Pageable pageable
	) {
		validateServiceDate(serviceDate);
		validatePageable(pageable);
		ShepherdRequesterAccessRow requester = requireActiveRequester(campusId, requesterId);
		requireCampusManagerOrAdmin(requester);
		List<ShepherdAttendanceBoardGroupRow> rows = groupRepository.findAdminAttendanceBoardRows(
			campusId, serviceDate, pageable);
		Map<Long, List<ShepherdGroupAssigneeResult>> assignees = assigneeMap(
			rows.stream().map(ShepherdAttendanceBoardGroupRow::groupId).toList());
		ShepherdAttendanceSummaryRow summary = reportRepository.summarizeCampusServiceDate(campusId, serviceDate);
		List<ShepherdAttendanceBoardGroupResult> groups = rows.stream()
			.map(row -> new ShepherdAttendanceBoardGroupResult(
				row.groupId(),
				row.groupName(),
				row.groupVersion(),
				assignees.getOrDefault(row.groupId(), List.of()),
				toReportResult(row)
			))
			.toList();
		long submitted = summary == null ? 0 : summary.submittedCount();
		long totalGroups = summary == null ? 0 : summary.totalGroupCount();
		int totalPages = pageable.getPageSize() == 0 ? 0 : (int) Math.ceil((double) totalGroups / pageable.getPageSize());
		return new ShepherdAttendanceBoardResult(
			campusId,
			serviceDate,
			pageable.getPageNumber(),
			pageable.getPageSize(),
			totalGroups,
			totalPages,
			submitted,
			totalGroups - submitted,
			summary == null ? 0 : summary.smallGroupMeetingCount(),
			summary == null ? 0 : summary.holyWaveCount(),
			summary == null ? 0 : summary.otherWorshipCount(),
			groups
		);
	}

	private ShepherdRequesterAccessRow requireActiveRequester(Long campusId, Long requesterId) {
		ShepherdRequesterAccessRow requester = accessRepository.findRequesterAccess(campusId, requesterId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!requester.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return requester;
	}

	private void requireActiveMembershipOrAdmin(ShepherdRequesterAccessRow requester, ErrorCode errorCode) {
		if (requester.isServiceAdmin() || requester.hasActiveMembership()) {
			return;
		}
		throw new BusinessException(errorCode);
	}

	private void requireCampusManagerOrAdmin(ShepherdRequesterAccessRow requester) {
		if (requester.isServiceAdmin() || requester.isCampusManager()) {
			return;
		}
		throw new BusinessException(ErrorCode.SHEPHERD_GROUP_MANAGE_FORBIDDEN);
	}

	private void requireGroupReadable(Long campusId, Long groupId, ShepherdRequesterAccessRow requester) {
		groupRepository.findByCampusIdAndIdAndStatus(campusId, groupId, ShepherdGroupStatus.ACTIVE)
			.orElseThrow(() -> new BusinessException(ErrorCode.SHEPHERD_GROUP_NOT_FOUND));
		if (requester.isServiceAdmin() || requester.isCampusManager()
			|| assigneeRepository.existsByCampusIdAndShepherdGroupIdAndUserId(campusId, groupId, requester.userId())) {
			return;
		}
		throw new BusinessException(ErrorCode.SHEPHERD_GROUP_NOT_FOUND);
	}

	private ShepherdGroup requireGroupWritable(Long campusId, Long groupId, ShepherdRequesterAccessRow requester) {
		ShepherdGroup group = groupRepository.findActiveByCampusIdAndIdForUpdate(campusId, groupId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SHEPHERD_GROUP_NOT_FOUND));
		if (requester.isServiceAdmin() || requester.isCampusManager()
			|| assigneeRepository.existsByCampusIdAndShepherdGroupIdAndUserId(campusId, groupId, requester.userId())) {
			return group;
		}
		throw new BusinessException(ErrorCode.SHEPHERD_GROUP_NOT_FOUND);
	}

	private List<Long> distinctAssigneeIds(List<Long> userIds, boolean requireNonEmpty) {
		if (userIds == null || userIds.isEmpty()) {
			if (requireNonEmpty) {
				throw new BusinessException(ErrorCode.SHEPHERD_GROUP_ASSIGNEE_REQUIRED);
			}
			return List.of();
		}
		List<Long> distinct = new ArrayList<>();
		Set<Long> seen = new LinkedHashSet<>();
		for (Long userId : userIds) {
			if (userId == null || !seen.add(userId)) {
				throw new BusinessException(ErrorCode.SHEPHERD_GROUP_ASSIGNEE_INVALID);
			}
			distinct.add(userId);
		}
		return distinct;
	}

	private void validateActiveSameCampusMembers(Long campusId, Collection<Long> userIds) {
		List<CampusMember> members = campusMemberRepository.findByCampusIdAndUserIdInAndStatus(
			campusId, userIds, CampusMemberStatus.ACTIVE);
		Set<Long> activeUserIds = members.stream().map(CampusMember::userId).collect(Collectors.toSet());
		if (!activeUserIds.containsAll(userIds)) {
			throw new BusinessException(ErrorCode.SHEPHERD_GROUP_ASSIGNEE_INVALID);
		}
	}

	private void saveAssignees(Long groupId, Long campusId, List<Long> userIds, Long requesterId) {
		for (Long userId : userIds) {
			assigneeRepository.save(ShepherdGroupAssignee.create(groupId, campusId, userId, requesterId));
		}
	}

	private ShepherdGroupResult toGroupResult(ShepherdGroup group) {
		return new ShepherdGroupResult(
			group.id(),
			group.campusId(),
			group.name(),
			group.status().name(),
			group.version(),
			assigneeMap(List.of(group.id())).getOrDefault(group.id(), List.of())
		);
	}

	private List<ShepherdGroupResult> toGroupResults(List<ShepherdGroupRow> rows) {
		Map<Long, List<ShepherdGroupAssigneeResult>> assignees = assigneeMap(
			rows.stream().map(ShepherdGroupRow::groupId).toList());
		return rows.stream()
			.map(row -> new ShepherdGroupResult(
				row.groupId(),
				row.campusId(),
				row.name(),
				row.status(),
				row.version(),
				assignees.getOrDefault(row.groupId(), List.of())
			))
			.toList();
	}

	private Map<Long, List<ShepherdGroupAssigneeResult>> assigneeMap(Collection<Long> groupIds) {
		if (groupIds == null || groupIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<ShepherdGroupAssigneeResult>> result = new LinkedHashMap<>();
		for (ShepherdGroupAssigneeRow row : assigneeRepository.findAssigneeRowsByGroupIds(groupIds)) {
			result.computeIfAbsent(row.groupId(), ignored -> new ArrayList<>())
				.add(new ShepherdGroupAssigneeResult(row.userId(), row.name(), row.email()));
		}
		return result;
	}

	private ShepherdAttendanceReportResult toReportResult(WeeklyShepherdAttendanceReport report, String lastModifiedByName) {
		return new ShepherdAttendanceReportResult(
			report.id(),
			report.campusId(),
			report.shepherdGroupId(),
			report.serviceDate(),
			report.smallGroupMeetingCount(),
			report.holyWaveCount(),
			report.otherWorshipCount(),
			report.note(),
			report.status().name(),
			report.lastModifiedBy(),
			lastModifiedByName,
			report.lastModifiedAt(),
			report.version()
		);
	}

	private ShepherdAttendanceReportResult toReportResult(ShepherdAttendanceReportRow row) {
		return new ShepherdAttendanceReportResult(
			row.reportId(),
			row.campusId(),
			row.groupId(),
			row.serviceDate(),
			row.smallGroupMeetingCount(),
			row.holyWaveCount(),
			row.otherWorshipCount(),
			row.note(),
			row.status(),
			row.lastModifiedByUserId(),
			row.lastModifiedByName(),
			row.lastModifiedAt(),
			row.version()
		);
	}

	private ShepherdAttendanceReportResult toReportResult(ShepherdAttendanceBoardGroupRow row) {
		if (row.reportId() == null) {
			return null;
		}
		return new ShepherdAttendanceReportResult(
			row.reportId(),
			null,
			row.groupId(),
			row.serviceDate(),
			row.smallGroupMeetingCount(),
			row.holyWaveCount(),
			row.otherWorshipCount(),
			row.note(),
			row.reportStatus(),
			row.lastModifiedByUserId(),
			row.lastModifiedByName(),
			row.lastModifiedAt(),
			row.reportVersion()
		);
	}

	private ShepherdHomeGroupResult toHomeGroupResult(ShepherdHomeGroupRow row) {
		return new ShepherdHomeGroupResult(row.groupId(), row.groupName(), toHomeReportResult(row));
	}

	private ShepherdHomeReportResult toHomeReportResult(ShepherdHomeGroupRow row) {
		if (row.reportId() == null) {
			return null;
		}
		return new ShepherdHomeReportResult(
			row.reportId(),
			row.smallGroupMeetingCount(),
			row.holyWaveCount(),
			row.otherWorshipCount(),
			row.note(),
			row.reportStatus(),
			row.reportVersion(),
			row.lastModifiedAt()
		);
	}

	private void validateServiceDate(LocalDate serviceDate) {
		if (serviceDate == null || serviceDate.getDayOfWeek() != DayOfWeek.SUNDAY) {
			throw new BusinessException(ErrorCode.SHEPHERD_INVALID_SERVICE_DATE);
		}
	}

	private void validateCounts(Integer smallGroupMeetingCount, Integer holyWaveCount, Integer otherWorshipCount) {
		if (smallGroupMeetingCount == null || holyWaveCount == null || otherWorshipCount == null
			|| smallGroupMeetingCount < 0 || holyWaveCount < 0 || otherWorshipCount < 0) {
			throw new BusinessException(ErrorCode.SHEPHERD_INVALID_ATTENDANCE_COUNT);
		}
	}

	private WeeklyShepherdAttendanceStatus parseStatus(String status) {
		try {
			return WeeklyShepherdAttendanceStatus.valueOf(status);
		} catch (RuntimeException exception) {
			throw new BusinessException(ErrorCode.SHEPHERD_INVALID_ATTENDANCE_STATUS);
		}
	}

	private String normalizeDisplayName(String name) {
		String normalized = name == null ? "" : name.trim().replaceAll("\\s+", " ");
		if (normalized.isBlank() || normalized.length() > 100) {
			throw new BusinessException(ErrorCode.SHEPHERD_GROUP_ASSIGNEE_INVALID);
		}
		return normalized;
	}

	private String normalizeLookupName(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private String normalizeNote(String note) {
		if (note == null) {
			return null;
		}
		String trimmed = note.trim();
		if (trimmed.length() > MAX_NOTE_LENGTH) {
			throw new BusinessException(ErrorCode.SHEPHERD_INVALID_ATTENDANCE_COUNT);
		}
		return trimmed.isEmpty() ? null : trimmed;
	}

	private void validatePageable(Pageable pageable) {
		validatePageRequest(pageable.getPageNumber(), pageable.getPageSize());
	}

	private void validatePageRequest(int page, int size) {
		if (page < 0) {
			throw new BusinessException(ErrorCode.SHEPHERD_INVALID_PAGE);
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new BusinessException(ErrorCode.SHEPHERD_INVALID_SIZE);
		}
	}

	private boolean isShepherdGroupNameUniqueViolation(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolation
				&& containsShepherdGroupNameConstraint(constraintViolation.getConstraintName())) {
				return true;
			}
			if (containsShepherdGroupNameConstraint(current.getMessage())) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private boolean containsShepherdGroupNameConstraint(String value) {
		return value != null && value.toLowerCase(Locale.ROOT)
			.contains("uk_shepherd_groups_campus_normalized_name");
	}
}
