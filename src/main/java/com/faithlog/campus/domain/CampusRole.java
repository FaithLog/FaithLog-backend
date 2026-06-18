package com.faithlog.campus.domain;

public enum CampusRole {
	MINISTER(4),
	ELDER(3),
	CAMPUS_LEADER(2),
	MEMBER(1);

	private final int authorityLevel;

	CampusRole(int authorityLevel) {
		this.authorityLevel = authorityLevel;
	}

	public boolean canManageCampusMembers() {
		return this != MEMBER;
	}

	public boolean canChangeCampusRole(CampusRole currentRole, CampusRole newRole) {
		return this != MEMBER && canReach(currentRole) && canReach(newRole);
	}

	private boolean canReach(CampusRole role) {
		return authorityLevel >= role.authorityLevel;
	}
}
