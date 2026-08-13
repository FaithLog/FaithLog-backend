package com.faithlog.shepherd.service.result;

import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.user.domain.type.UserRole;

public record ShepherdRequesterAccessRow(
	Long userId,
	String name,
	String email,
	UserRole userRole,
	boolean active,
	Long membershipId,
	CampusRole campusRole,
	CampusMemberStatus membershipStatus
) {

	public boolean isServiceAdmin() {
		return userRole == UserRole.ADMIN;
	}

	public boolean hasActiveMembership() {
		return membershipId != null && membershipStatus == CampusMemberStatus.ACTIVE;
	}

	public boolean isCampusManager() {
		return hasActiveMembership() && campusRole != null && campusRole.canManageCampusMembers();
	}
}
