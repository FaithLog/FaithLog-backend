package com.faithlog.campus.application;

import com.faithlog.campus.domain.CampusMemberStatus;
import com.faithlog.campus.domain.CampusRole;

public record CampusMembershipRow(
	Long membershipId,
	Long campusId,
	String campusName,
	String region,
	CampusRole campusRole,
	CampusMemberStatus status
) {

	public CampusMembershipResult toResult() {
		return new CampusMembershipResult(
			membershipId,
			campusId,
			campusName,
			region,
			campusRole.name(),
			status.name()
		);
	}
}
