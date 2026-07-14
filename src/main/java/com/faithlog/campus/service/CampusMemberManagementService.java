package com.faithlog.campus.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.command.ChangeCampusRoleCommand;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.policy.CampusRolePolicy;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberLockScope;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.port.CampusUserTokenVersionPort;
import com.faithlog.campus.service.result.AdminCampusMemberResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusMemberManagementService {

	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final CampusUserTokenVersionPort userTokenVersionPort;
	private final CampusAccessPolicy campusAccessPolicy;

	public CampusMemberManagementService(
		CampusMemberRepositoryPort campusMemberRepository,
		CampusRepositoryPort campusRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		CampusUserTokenVersionPort userTokenVersionPort,
		CampusAccessPolicy campusAccessPolicy
	) {
		this.campusMemberRepository = campusMemberRepository;
		this.campusRepository = campusRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.userTokenVersionPort = userTokenVersionPort;
		this.campusAccessPolicy = campusAccessPolicy;
	}

	@Transactional(readOnly = true)
	public List<AdminCampusMemberResult> getCampusMembers(Long campusId, Long requesterId) {
		campusAccessPolicy.requireCampusManager(
			campusId,
			requesterId,
			ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN,
			"캠퍼스 멤버 관리 권한이 없습니다."
		);
		return campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE)
			.stream()
			.map(member -> AdminCampusMemberResult.of(member, campusAccessPolicy.getUserOrThrow(member.userId())))
			.toList();
	}

	@Transactional
	public void deleteCampusMember(Long campusId, Long membershipId, Long requesterId) {
		CampusMemberLockScope targetScope = campusMemberRepository.findLockScopeByCampusIdAndId(campusId, membershipId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));
		Map<Long, CampusUserLookupResult> lockedUsers = campusAccessPolicy.getUsersForUpdate(
			List.of(requesterId, targetScope.userId()));
		CampusUserLookupResult requester = requireActiveRequester(lockedUsers.get(requesterId));
		campusRepository.findByIdForUpdate(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		if (!requester.isAdmin()) {
			CampusMember requesterMembership = campusMemberRepository
				.findByCampusIdAndUserIdForUpdate(campusId, requester.userId())
				.filter(CampusMember::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN));
			CampusRolePolicy.requireCampusManager(requesterMembership, ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN);
		}
		var activeDuties = dutyAssignmentRepository.findActiveByCampusIdAndUserIdForUpdate(
			campusId, targetScope.userId());
		CampusMember targetMember = campusMemberRepository.findByCampusIdAndIdForUpdate(campusId, membershipId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));
		if (!activeDuties.isEmpty()) {
			throw new BusinessException(ErrorCode.CAMPUS_MEMBER_ACTIVE_DUTY_CONFLICT);
		}
		targetMember.deactivate();
	}

	@Transactional
	public AdminCampusMemberResult changeCampusRole(ChangeCampusRoleCommand command) {
		CampusMemberLockScope targetScope = campusMemberRepository
			.findLockScopeByCampusIdAndId(command.campusId(), command.campusMemberId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));
		Map<Long, CampusUserLookupResult> lockedUsers = campusAccessPolicy.getUsersForUpdate(
			List.of(command.requesterId(), targetScope.userId()));
		CampusUserLookupResult requester = requireActiveRequester(lockedUsers.get(command.requesterId()));
		campusRepository.findByIdForUpdate(command.campusId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		CampusMember targetMember = campusMemberRepository
			.findByCampusIdAndIdForUpdate(command.campusId(), command.campusMemberId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));

		if (!requester.isAdmin()) {
			CampusMember requesterMembership = campusMemberRepository
				.findByCampusIdAndUserIdForUpdate(command.campusId(), requester.userId())
				.filter(CampusMember::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_ROLE_CHANGE_FORBIDDEN));
			CampusRolePolicy.requireRoleChangeAllowed(
				requesterMembership,
				targetMember.campusRole(),
				command.campusRole()
			);
		}

		CampusUserLookupResult targetUser = lockedUsers.get(targetScope.userId());
		if (targetMember.campusRole() != command.campusRole()) {
			targetMember.changeCampusRole(command.campusRole());
			userTokenVersionPort.increaseTokenVersion(targetMember.userId());
		}
		return AdminCampusMemberResult.of(targetMember, targetUser);
	}

	private CampusUserLookupResult requireActiveRequester(CampusUserLookupResult requester) {
		if (requester == null || !requester.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return requester;
	}
}
