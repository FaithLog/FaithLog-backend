package com.faithlog.campus.controller.dto.response;

import com.faithlog.campus.service.result.CampusMembershipResult;

public record CampusMembershipResponse(
	Long membershipId,
	Long campusId,
	String campusName,
	String region,
	String campusRole,
	String status
) {

	public static CampusMembershipResponse from(CampusMembershipResult result) {
		return new CampusMembershipResponse(
			result.membershipId(),
			result.campusId(),
			result.campusName(),
			result.region(),
			result.campusRole(),
			result.status()
		);
	}
}
