package com.faithlog.announcement.service.policy;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementAccessPolicy {

	private final CampusAccessPolicy campusAccessPolicy;
	private final CampusMemberRepositoryPort campusMemberRepository;

	public AnnouncementAccessPolicy(
		CampusAccessPolicy campusAccessPolicy,
		CampusMemberRepositoryPort campusMemberRepository
	) {
		this.campusAccessPolicy = campusAccessPolicy;
		this.campusMemberRepository = campusMemberRepository;
	}

	public void requireManager(Long campusId, Long requesterId) {
		campusAccessPolicy.requireCampusManager(
			campusId,
			requesterId,
			ErrorCode.ANNOUNCEMENT_MANAGE_FORBIDDEN,
			ErrorCode.ANNOUNCEMENT_MANAGE_FORBIDDEN.message()
		);
	}

	public void requireActiveMember(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = campusAccessPolicy.getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember membership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.ANNOUNCEMENT_ACCESS_FORBIDDEN));
		if (!membership.isActive()) {
			throw new BusinessException(ErrorCode.ANNOUNCEMENT_ACCESS_FORBIDDEN);
		}
	}
}
