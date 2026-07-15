package com.faithlog.admin.service;

import com.faithlog.admin.service.command.ChangeUserRoleCommand;
import com.faithlog.admin.service.policy.AdminAccessPolicy;
import com.faithlog.admin.service.port.AdminCampusMemberRepositoryPort;
import com.faithlog.admin.service.port.AdminUserRepositoryPort;
import com.faithlog.admin.service.query.AdminUserSearchCriteria;
import com.faithlog.admin.service.result.AdminUserCampusResult;
import com.faithlog.admin.service.result.AdminUserResult;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserManagementService {

	private final AdminUserRepositoryPort userRepository;
	private final CampusRepositoryPort campusRepository;
	private final AdminCampusMemberRepositoryPort adminCampusMemberRepository;

	public AdminUserManagementService(
		AdminUserRepositoryPort userRepository,
		CampusRepositoryPort campusRepository,
		AdminCampusMemberRepositoryPort adminCampusMemberRepository
	) {
		this.userRepository = userRepository;
		this.campusRepository = campusRepository;
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
		User mutationLock = userRepository.findFirstAdminMutationLockForUpdate()
			.orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND));
		Map<Long, User> lockedUsers = lockUsers(command.requesterId(), command.userId(), mutationLock);
		AdminAccessPolicy.requireServiceAdmin(lockedUsers.get(command.requesterId()));
		User user = lockedUsers.get(command.userId());
		if (user.role() == UserRole.ADMIN
			&& command.role() != UserRole.ADMIN
			&& user.isActive()
			&& userRepository.countByRoleAndIsActiveTrue(UserRole.ADMIN) <= 1) {
			throw new BusinessException(ErrorCode.ADMIN_LAST_ADMIN_DEMOTION_FORBIDDEN);
		}
		user.changeRole(command.role());
		return AdminUserResult.of(user, userCampuses(user.id()));
	}

	private Map<Long, User> lockUsers(Long requesterId, Long targetId, User mutationLock) {
		List<Long> userIds = java.util.stream.Stream.of(requesterId, targetId)
			.distinct()
			.filter(userId -> !userId.equals(mutationLock.id()))
			.sorted()
			.toList();
		Map<Long, User> users = userIds.isEmpty()
			? new java.util.HashMap<>()
			: userRepository.findAdminUsersByIdsForUpdate(userIds)
			.stream()
			.collect(Collectors.toMap(User::id, Function.identity(), (left, right) -> left, java.util.HashMap::new));
		if (requesterId.equals(mutationLock.id()) || targetId.equals(mutationLock.id())) {
			users.put(mutationLock.id(), mutationLock);
		}
		java.util.Set<Long> requiredUserIds = java.util.stream.Stream.of(requesterId, targetId)
			.collect(java.util.stream.Collectors.toSet());
		if (!users.keySet().containsAll(requiredUserIds)) {
			throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND);
		}
		return users;
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

	private Campus getCampusOrThrow(Long campusId) {
		return campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}
}
