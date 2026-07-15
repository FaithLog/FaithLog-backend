package com.faithlog.admin.service;

import com.faithlog.admin.service.command.AddCampusMemberCommand;
import com.faithlog.admin.service.policy.AdminAccessPolicy;
import com.faithlog.admin.service.port.AdminCampusRepositoryPort;
import com.faithlog.admin.service.port.AdminUserRepositoryPort;
import com.faithlog.admin.service.query.AdminCampusSearchCriteria;
import com.faithlog.admin.service.result.AdminCampusResult;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.result.AdminCampusMemberResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCampusManagementService {

	private final AdminUserRepositoryPort userRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final AdminCampusRepositoryPort adminCampusRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;

	public AdminCampusManagementService(
		AdminUserRepositoryPort userRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		AdminCampusRepositoryPort adminCampusRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository
	) {
		this.userRepository = userRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.adminCampusRepository = adminCampusRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
	}

	@Transactional(readOnly = true)
	public Page<AdminCampusResult> searchCampuses(
		Long requesterId,
		AdminCampusSearchCriteria criteria,
		Pageable pageable
	) {
		requireAdmin(requesterId);
		return adminCampusRepository.searchAdminCampuses(criteria, pageable).map(this::campusResult);
	}

	@Transactional
	public AdminCampusMemberResult addCampusMember(AddCampusMemberCommand command) {
		Map<Long, User> lockedUsers = lockUsers(command.requesterId(), command.userId());
		AdminAccessPolicy.requireServiceAdmin(lockedUsers.get(command.requesterId()));
		User user = lockedUsers.get(command.userId());
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND);
		}
		Campus campus = campusRepository.findByIdForUpdate(command.campusId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		var activeDuties = dutyAssignmentRepository.findActiveByCampusIdAndUserIdForUpdate(
			campus.id(), user.id());
		CampusMember existingMember = campusMemberRepository
			.findByCampusIdAndUserIdForUpdate(campus.id(), user.id())
			.orElse(null);
		if (existingMember != null && existingMember.isActive()) {
			throw new BusinessException(ErrorCode.CAMPUS_ALREADY_JOINED);
		}
		if (existingMember != null) {
			if (!activeDuties.isEmpty()) {
				throw new BusinessException(ErrorCode.CAMPUS_MEMBER_ACTIVE_DUTY_CONFLICT);
			}
			existingMember.reactivateAsMember();
			return AdminCampusMemberResult.of(existingMember, campusUser(user));
		}
		CampusMember member = campusMemberRepository.save(CampusMember.createMember(campus.id(), user.id()));
		return AdminCampusMemberResult.of(member, campusUser(user));
	}

	private Map<Long, User> lockUsers(Long requesterId, Long targetId) {
		List<Long> userIds = java.util.stream.Stream.of(requesterId, targetId)
			.distinct()
			.sorted()
			.toList();
		Map<Long, User> users = userRepository.findAdminUsersByIdsForUpdate(userIds)
			.stream()
			.collect(Collectors.toMap(User::id, Function.identity()));
		if (!users.keySet().containsAll(userIds)) {
			throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND);
		}
		return users;
	}

	private AdminCampusResult campusResult(Campus campus) {
		List<CampusMember> activeMembers = campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(
			campus.id(),
			CampusMemberStatus.ACTIVE
		);
		return AdminCampusResult.of(campus, activeMembers);
	}

	private User requireAdmin(Long requesterId) {
		User requester = getUserOrThrow(requesterId);
		AdminAccessPolicy.requireServiceAdmin(requester);
		return requester;
	}

	private User getUserOrThrow(Long userId) {
		return userRepository.findAdminUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND));
	}

	private CampusUserLookupResult campusUser(User user) {
		return new CampusUserLookupResult(
			user.id(),
			user.name(),
			user.email(),
			user.role().name(),
			user.isActive()
		);
	}

	private Campus getCampusOrThrow(Long campusId) {
		return campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}
}
