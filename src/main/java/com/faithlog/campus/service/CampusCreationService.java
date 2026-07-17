package com.faithlog.campus.service;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.policy.CampusRolePolicy;
import com.faithlog.campus.service.port.CampusCreationSideEffectPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusCreationService {

	private static final int INVITE_CODE_MAX_ATTEMPTS = 20;

	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final List<CampusCreationSideEffectPort> campusCreationSideEffectPorts;
	private final InviteCodeGenerator inviteCodeGenerator;
	private final CampusAccessPolicy campusAccessPolicy;

	public CampusCreationService(
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		List<CampusCreationSideEffectPort> campusCreationSideEffectPorts,
		InviteCodeGenerator inviteCodeGenerator,
		CampusAccessPolicy campusAccessPolicy
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.campusCreationSideEffectPorts = campusCreationSideEffectPorts;
		this.inviteCodeGenerator = inviteCodeGenerator;
		this.campusAccessPolicy = campusAccessPolicy;
	}

	@Transactional
	public CampusCreateResult createCampus(CreateCampusCommand command) {
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUserForUpdate(command.requesterId());
		CampusRolePolicy.requireCampusCreator(requester);

		Campus campus = campusRepository.save(Campus.create(
			command.name(),
			command.region(),
			command.description(),
			generateUniqueInviteCode()
		));
		CampusMember creatorMembership = campusMemberRepository.save(
			CampusMember.createMinister(campus.id(), requester.userId())
		);
		campusCreationSideEffectPorts.forEach(port -> port.afterCampusCreated(campus.id()));
		return CampusCreateResult.of(campus, creatorMembership);
	}

	private String generateUniqueInviteCode() {
		for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
			String inviteCode = inviteCodeGenerator.generate();
			if (!campusRepository.existsByInviteCode(inviteCode)) {
				return inviteCode;
			}
		}
		throw new BusinessException(ErrorCode.CAMPUS_INVITE_CODE_GENERATION_FAILED);
	}
}
