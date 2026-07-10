package com.faithlog.campus.service;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.command.UpdateCampusCommand;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.policy.CampusRolePolicy;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.result.CampusDetailResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusUpdateService {

	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusAccessPolicy campusAccessPolicy;

	public CampusUpdateService(
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusAccessPolicy campusAccessPolicy
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.campusAccessPolicy = campusAccessPolicy;
	}

	@Transactional
	public CampusDetailResult updateCampus(UpdateCampusCommand command) {
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUser(command.requesterId());
		Campus campus = campusRepository.findById(command.campusId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		CampusMember membership = campusMemberRepository
			.findByCampusIdAndUserId(campus.id(), requester.userId())
			.orElse(null);

		if (!requester.isAdmin()) {
			if (membership == null || !membership.isActive()) {
				throw new BusinessException(ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN);
			}
			CampusRolePolicy.requireCampusManager(membership, ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN);
		}

		campus.update(command.name(), command.region(), command.description(), command.isActive());
		return CampusDetailResult.of(
			campus,
			membership,
			requester.isAdmin() || (membership != null && membership.canViewInviteCode())
		);
	}
}
