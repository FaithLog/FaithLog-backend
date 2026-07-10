package com.faithlog.admin.service;

import com.faithlog.admin.service.command.AddCampusMemberCommand;
import com.faithlog.admin.service.command.ChangeUserRoleCommand;
import com.faithlog.admin.service.query.AdminCampusSearchCriteria;
import com.faithlog.admin.service.query.AdminUserSearchCriteria;
import com.faithlog.admin.service.result.AdminCampusResult;
import com.faithlog.admin.service.result.AdminUserCampusResult;
import com.faithlog.admin.service.result.AdminUserResult;
import com.faithlog.admin.service.port.AdminCampusMemberRepositoryPort;
import com.faithlog.admin.service.port.AdminCampusRepositoryPort;
import com.faithlog.admin.service.port.AdminUserRepositoryPort;
import com.faithlog.campus.service.result.AdminCampusMemberResult;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminManagementService {

	private final AdminUserRepositoryPort userRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final AdminCampusRepositoryPort adminCampusRepository;
	private final AdminCampusMemberRepositoryPort adminCampusMemberRepository;

	public AdminManagementService(
		AdminUserRepositoryPort userRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		AdminCampusRepositoryPort adminCampusRepository,
		AdminCampusMemberRepositoryPort adminCampusMemberRepository
	) {
		this.userRepository = userRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.adminCampusRepository = adminCampusRepository;
		this.adminCampusMemberRepository = adminCampusMemberRepository;
	}

	@Transactional(readOnly = true)
	public Page<AdminUserResult> searchUsers(Long requesterId, AdminUserSearchCriteria criteria, Pageable pageable) {
		requireAdmin(requesterId);
		return userRepository.searchAdminUsers(criteria, pageable)
			.map(user -> AdminUserResult.of(user, userCampuses(user.id())));
	}

	@Transactional(readOnly = true)
	public AdminUserResult getUser(Long requesterId, Long userId) {
		requireAdmin(requesterId);
		User user = getUserOrThrow(userId);
		return AdminUserResult.of(user, userCampuses(user.id()));
	}

	@Transactional
	public AdminUserResult changeUserRole(ChangeUserRoleCommand command) {
		requireAdmin(command.requesterId());
		User user = getUserOrThrow(command.userId());
		if (user.role() == UserRole.ADMIN
			&& command.role() != UserRole.ADMIN
			&& user.isActive()
			&& userRepository.countByRoleAndIsActiveTrue(UserRole.ADMIN) <= 1) {
			throw new BusinessException(ErrorCode.ADMIN_LAST_ADMIN_DEMOTION_FORBIDDEN);
		}
		user.changeRole(command.role());
		return AdminUserResult.of(user, userCampuses(user.id()));
	}

	@Transactional(readOnly = true)
	public Page<AdminCampusResult> searchCampuses(Long requesterId, AdminCampusSearchCriteria criteria, Pageable pageable) {
		requireAdmin(requesterId);
		return adminCampusRepository.searchAdminCampuses(criteria, pageable)
			.map(this::campusResult);
	}

	@Transactional
	public AdminCampusMemberResult addCampusMember(AddCampusMemberCommand command) {
		requireAdmin(command.requesterId());
		Campus campus = getCampusOrThrow(command.campusId());
		User user = getUserOrThrow(command.userId());
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND);
		}
		CampusMember existingMember = campusMemberRepository.findByCampusIdAndUserId(campus.id(), user.id()).orElse(null);
		if (existingMember != null && existingMember.isActive()) {
			throw new BusinessException(ErrorCode.CAMPUS_ALREADY_JOINED);
		}
		if (existingMember != null) {
			existingMember.reactivateAsMember();
			return AdminCampusMemberResult.of(existingMember, campusUser(user));
		}
		CampusMember member = campusMemberRepository.save(CampusMember.createMember(campus.id(), user.id()));
		return AdminCampusMemberResult.of(member, campusUser(user));
	}

	private AdminCampusResult campusResult(Campus campus) {
		List<CampusMember> activeMembers = campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(
			campus.id(),
			CampusMemberStatus.ACTIVE
		);
		return AdminCampusResult.of(campus, activeMembers);
	}

	private List<AdminUserCampusResult> userCampuses(Long userId) {
		return adminCampusMemberRepository.findByUserIdOrderByIdAsc(userId)
			.stream()
			.map(member -> AdminUserCampusResult.of(member, getCampusOrThrow(member.campusId())))
			.toList();
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
