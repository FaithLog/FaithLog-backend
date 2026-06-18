package com.faithlog.campus.application;

import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.domain.CampusMember;

public record AdminCampusMemberResult(
	Long membershipId,
	Long campusId,
	Long userId,
	String name,
	String email,
	String campusRole,
	String status
) {

	public static AdminCampusMemberResult of(CampusMember member, CampusUserLookupResult user) {
		return new AdminCampusMemberResult(
			member.id(),
			member.campusId(),
			member.userId(),
			user.name(),
			user.email(),
			member.campusRole().name(),
			member.status().name()
		);
	}
}
