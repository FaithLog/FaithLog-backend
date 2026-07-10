package com.faithlog.devotion.application;

import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.policy.CampusRolePolicy;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.devotion.domain.PenaltyRule;
import com.faithlog.devotion.domain.PenaltyRuleType;
import com.faithlog.devotion.infrastructure.jpa.PenaltyRuleRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PenaltyRuleService {

	private final PenaltyRuleRepository penaltyRuleRepository;
	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;

	public PenaltyRuleService(
		PenaltyRuleRepository penaltyRuleRepository,
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort
	) {
		this.penaltyRuleRepository = penaltyRuleRepository;
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
	}

	@Transactional(readOnly = true)
	public List<PenaltyRuleResult> listPenaltyRules(Long campusId, Long requesterId) {
		requirePenaltyRuleListAccess(campusId, requesterId);
		return penaltyRuleRepository.findByCampusIdOrderByIdAsc(campusId)
			.stream()
			.map(PenaltyRuleResult::from)
			.toList();
	}

	@Transactional
	public PenaltyRuleResult createPenaltyRule(CreatePenaltyRuleCommand command) {
		requireCampusManager(command.campusId(), command.requesterId());
		lockCampusOrThrow(command.campusId());

		PenaltyRule rule = PenaltyRule.create(
			command.campusId(),
			command.ruleType(),
			command.calculationType(),
			command.requiredCount(),
			command.baseAmount(),
			command.amountPerUnit()
		);
		deactivateActiveRules(command.campusId(), command.ruleType());
		return PenaltyRuleResult.from(penaltyRuleRepository.save(rule));
	}

	@Transactional
	public PenaltyRuleResult updatePenaltyRule(UpdatePenaltyRuleCommand command) {
		PenaltyRule rule = penaltyRuleRepository.findById(command.ruleId())
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVOTION_PENALTY_RULE_NOT_FOUND));
		requireCampusManager(rule.campusId(), command.requesterId());
		lockCampusOrThrow(rule.campusId());
		if (command.isActive()) {
			deactivateOtherActiveRules(rule);
		}
		rule.update(command.requiredCount(), command.baseAmount(), command.amountPerUnit(), command.isActive());
		return PenaltyRuleResult.from(rule);
	}

	private void deactivateOtherActiveRules(PenaltyRule rule) {
		penaltyRuleRepository.findByCampusIdAndRuleTypeAndIsActiveTrue(rule.campusId(), rule.ruleType())
			.stream()
			.filter(activeRule -> !activeRule.id().equals(rule.id()))
			.forEach(PenaltyRule::deactivate);
	}

	private void deactivateActiveRules(Long campusId, PenaltyRuleType ruleType) {
		penaltyRuleRepository.findByCampusIdAndRuleTypeAndIsActiveTrue(campusId, ruleType)
			.forEach(PenaltyRule::deactivate);
	}

	private void requirePenaltyRuleListAccess(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		requireActiveCampusMember(campusId, requester.userId(), ErrorCode.DEVOTION_PENALTY_RULE_ACCESS_FORBIDDEN);
	}

	private void requireCampusManager(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVOTION_PENALTY_RULE_MANAGE_FORBIDDEN));
		CampusRolePolicy.requireCampusManager(requesterMembership, ErrorCode.DEVOTION_PENALTY_RULE_MANAGE_FORBIDDEN);
	}

	private void requireActiveCampusMember(Long campusId, Long userId, ErrorCode errorCode) {
		campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode));
	}

	private CampusUserLookupResult getActiveUser(Long userId) {
		CampusUserLookupResult user = userLookupPort.findCampusUserById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.active()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return user;
	}

	private void lockCampusOrThrow(Long campusId) {
		campusRepository.findByIdForUpdate(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
	}
}
