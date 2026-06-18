package com.faithlog.campus.application;

import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusMember;

public record CampusMembershipResult(
	Long membershipId,
	Long campusId,
	String campusName,
	String region,
	String campusRole,
	String status
) {

	public static CampusMembershipResult of(Campus campus, CampusMember member) {
		return new CampusMembershipResult(
			member.id(),
			campus.id(),
			campus.name(),
			campus.region(),
			member.campusRole().name(),
			member.status().name()
		);
	}
}
