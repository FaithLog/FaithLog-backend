package com.faithlog.campus.service.result;

import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.domain.type.CampusRole;

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
