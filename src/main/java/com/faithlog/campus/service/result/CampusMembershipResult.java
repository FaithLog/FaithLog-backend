package com.faithlog.campus.service.result;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;

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
