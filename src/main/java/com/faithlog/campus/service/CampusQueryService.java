package com.faithlog.campus.service;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.result.CampusDetailResult;
import com.faithlog.campus.service.result.CampusMembershipResult;
import com.faithlog.campus.service.result.CampusMembershipRow;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusQueryService {

	private final CampusRepositoryPort campusRepository;
	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusAccessPolicy campusAccessPolicy;

	public CampusQueryService(
		CampusRepositoryPort campusRepository,
		CampusMemberRepositoryPort campusMemberRepository,
		CampusAccessPolicy campusAccessPolicy
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.campusAccessPolicy = campusAccessPolicy;
	}

	@Transactional(readOnly = true)
	public List<CampusMembershipResult> getMyCampuses(Long requesterId) {
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUser(requesterId);
		return campusMemberRepository.findMembershipRowsByUserIdAndStatusOrderByIdDesc(
			requester.userId(),
			CampusMemberStatus.ACTIVE
		).stream().map(CampusMembershipRow::toResult).toList();
	}

	@Transactional(readOnly = true)
	public CampusDetailResult getCampus(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUser(requesterId);
		Campus campus = campusRepository.findById(campusId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CAMPUS_NOT_FOUND));
		CampusMember membership = campusMemberRepository
			.findByCampusIdAndUserId(campus.id(), requester.userId())
			.orElse(null);

		if (requester.isAdmin()) {
			return CampusDetailResult.of(campus, membership, true);
		}
		if (membership == null || !membership.isActive()) {
			throw new BusinessException(ErrorCode.CAMPUS_VIEW_FORBIDDEN);
		}
		return CampusDetailResult.of(campus, membership, membership.canViewInviteCode());
	}
}
