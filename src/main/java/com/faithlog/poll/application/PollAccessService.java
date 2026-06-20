package com.faithlog.poll.application;

import com.faithlog.campus.application.policy.CampusRolePolicy;
import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.application.port.CampusUserLookupPort;
import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
class PollAccessService {

	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusUserLookupPort userLookupPort;

	PollAccessService(CampusMemberRepositoryPort campusMemberRepository, CampusUserLookupPort userLookupPort) {
		this.campusMemberRepository = campusMemberRepository;
		this.userLookupPort = userLookupPort;
	}

	void requireTemplateManager(Long campusId, Long requesterId) {
		requireCampusManager(campusId, requesterId, ErrorCode.POLL_TEMPLATE_MANAGE_FORBIDDEN);
	}

	void requirePollCreator(Long campusId, Long requesterId) {
		requireCampusManager(campusId, requesterId, ErrorCode.POLL_CREATE_FORBIDDEN);
	}

	CampusMember requireActiveCampusMember(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		return campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_ACCESS_FORBIDDEN));
	}

	void requirePollReader(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_ACCESS_FORBIDDEN));
	}

	void requirePollAdmin(Long campusId, Long requesterId) {
		requireCampusManager(campusId, requesterId, ErrorCode.POLL_ADMIN_FORBIDDEN);
	}

	boolean hasAdminVisibility(Long campusId, Long requesterId) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return true;
		}
		return campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.map(member -> {
				try {
					CampusRolePolicy.requireCampusManager(member, ErrorCode.POLL_ADMIN_FORBIDDEN);
					return true;
				} catch (BusinessException exception) {
					return false;
				}
			})
			.orElse(false);
	}

	CampusUserLookupResult getUser(Long userId) {
		return getActiveUser(userId);
	}

	private void requireCampusManager(Long campusId, Long requesterId, ErrorCode errorCode) {
		CampusUserLookupResult requester = getActiveUser(requesterId);
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.userId())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode));
		CampusRolePolicy.requireCampusManager(requesterMembership, errorCode);
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
