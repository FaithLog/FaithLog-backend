package com.faithlog.campus.application;

import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusMember;

public record CampusDetailResult(
	Long campusId,
	String name,
	String region,
	String description,
	boolean isActive,
	String inviteCode,
	String myCampusRole,
	String membershipStatus
) {

	public static CampusDetailResult of(Campus campus, CampusMember member, boolean includeInviteCode) {
		return new CampusDetailResult(
			campus.id(),
			campus.name(),
			campus.region(),
			campus.description(),
			campus.isActive(),
			includeInviteCode ? campus.inviteCode() : null,
			member == null ? null : member.campusRole().name(),
			member == null ? null : member.status().name()
		);
	}
}
