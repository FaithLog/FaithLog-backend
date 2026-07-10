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
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.result.AdminCampusMemberResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import java.util.List;
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

	public AdminCampusManagementService(
		AdminUserRepositoryPort userRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		AdminCampusRepositoryPort adminCampusRepository
	) {
		this.userRepository = userRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.adminCampusRepository = adminCampusRepository;
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
		requireAdmin(command.requesterId());
		Campus campus = getCampusOrThrow(command.campusId());
		User user = getUserOrThrow(command.userId());
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND);
		}
		CampusMember existingMember = campusMemberRepository
			.findByCampusIdAndUserId(campus.id(), user.id())
			.orElse(null);
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
