package com.faithlog.devotion.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.devotion.infrastructure.repository.PenaltyRuleRepository;
import com.faithlog.devotion.service.result.PenaltyRuleResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PenaltyRuleQueryService {

	private final PenaltyRuleRepository penaltyRuleRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;

	public PenaltyRuleQueryService(
		PenaltyRuleRepository penaltyRuleRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusUserLookupPort userLookupPort
	) {
		this.penaltyRuleRepository = penaltyRuleRepository;
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

	private void requirePenaltyRuleListAccess(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		requireActiveCampusMember(campusId, requester.userId(), ErrorCode.DEVOTION_PENALTY_RULE_ACCESS_FORBIDDEN);
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
}
