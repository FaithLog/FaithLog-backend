package com.faithlog.campus.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.command.ChangeCampusRoleCommand;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.policy.CampusRolePolicy;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.port.CampusUserTokenVersionPort;
import com.faithlog.campus.service.result.AdminCampusMemberResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusMemberManagementService {

	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserTokenVersionPort userTokenVersionPort;
	private final CampusAccessPolicy campusAccessPolicy;

	public CampusMemberManagementService(
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserTokenVersionPort userTokenVersionPort,
		CampusAccessPolicy campusAccessPolicy
	) {
		this.campusMemberRepository = campusMemberRepository;
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
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUser(requesterId);
		CampusMember targetMember = campusMemberRepository.findByCampusIdAndId(campusId, membershipId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));

		if (!requester.isAdmin()) {
			CampusMember requesterMembership = campusMemberRepository
				.findByCampusIdAndUserId(campusId, requester.userId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN));
			CampusRolePolicy.requireCampusManager(requesterMembership, ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN);
		}
		targetMember.deactivate();
	}

	@Transactional
	public AdminCampusMemberResult changeCampusRole(ChangeCampusRoleCommand command) {
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUser(command.requesterId());
		CampusMember targetMember = campusMemberRepository
			.findByCampusIdAndId(command.campusId(), command.campusMemberId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_MEMBER_NOT_FOUND));

		if (!requester.isAdmin()) {
			CampusMember requesterMembership = campusMemberRepository
				.findByCampusIdAndUserId(command.campusId(), requester.userId())
				.filter(CampusMember::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_ROLE_CHANGE_FORBIDDEN));
			CampusRolePolicy.requireRoleChangeAllowed(
				requesterMembership,
				targetMember.campusRole(),
				command.campusRole()
			);
		}

		CampusUserLookupResult targetUser = campusAccessPolicy.getUserOrThrow(targetMember.userId());
		if (targetMember.campusRole() != command.campusRole()) {
			targetMember.changeCampusRole(command.campusRole());
			userTokenVersionPort.increaseTokenVersion(targetMember.userId());
		}
		return AdminCampusMemberResult.of(targetMember, targetUser);
	}
}
