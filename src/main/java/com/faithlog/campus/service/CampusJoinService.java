package com.faithlog.campus.service;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.result.CampusMembershipResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusJoinService {

	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusAccessPolicy campusAccessPolicy;

	public CampusJoinService(
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusAccessPolicy campusAccessPolicy
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.campusAccessPolicy = campusAccessPolicy;
	}

	@Transactional
	public CampusMembershipResult joinCampus(JoinCampusCommand command) {
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUser(command.requesterId());
		Campus campus = campusRepository.findByInviteCode(command.inviteCode())
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_INVALID_INVITE_CODE));

		CampusMember existingMember = campusMemberRepository
			.findByCampusIdAndUserId(campus.id(), requester.userId())
			.orElse(null);
		if (existingMember != null && existingMember.isActive()) {
			throw new BusinessException(ErrorCode.CAMPUS_ALREADY_JOINED);
		}
		if (existingMember != null) {
			existingMember.reactivateAsMember();
			return CampusMembershipResult.of(campus, existingMember);
		}

		CampusMember member = campusMemberRepository.save(CampusMember.createMember(campus.id(), requester.userId()));
		return CampusMembershipResult.of(campus, member);
	}
}
