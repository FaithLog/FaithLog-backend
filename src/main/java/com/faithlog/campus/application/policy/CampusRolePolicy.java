package com.faithlog.campus.application.policy;

import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusRole;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;

public final class CampusRolePolicy {

	private CampusRolePolicy() {
	}

	public static void requireCampusCreator(CampusUserLookupResult requester) {
		if (!requester.canCreateCampus()) {
			throw new BusinessException(ErrorCode.CAMPUS_CREATE_FORBIDDEN);
		}
	}

	public static void requireCampusManager(CampusMember requesterMembership, ErrorCode errorCode) {
		if (!requesterMembership.canManageCampusMembers()) {
			throw new BusinessException(errorCode);
		}
	}

	public static void requireCampusManager(CampusMember requesterMembership, ErrorCode errorCode, String message) {
		if (!requesterMembership.canManageCampusMembers()) {
			throw new BusinessException(errorCode, message);
		}
	}

	public static void requireRoleChangeAllowed(
		CampusMember requesterMembership,
		CampusRole currentRole,
		CampusRole newRole
	) {
		requireCampusManager(requesterMembership, ErrorCode.CAMPUS_ROLE_CHANGE_FORBIDDEN);
		if (!requesterMembership.campusRole().canChangeCampusRole(currentRole, newRole)) {
			throw new BusinessException(ErrorCode.CAMPUS_ROLE_HIERARCHY_FORBIDDEN);
		}
	}
}
