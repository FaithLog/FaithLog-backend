package com.faithlog.user.controller.dto.response;

import com.faithlog.user.service.result.CampusMembershipResult;

public record CampusMembershipResponse(
	Long campusMemberId,
	Long campusId,
	String campusName,
	String region,
	String campusRole,
	String status
) {

	public static CampusMembershipResponse from(CampusMembershipResult result) {
		return new CampusMembershipResponse(
			result.campusMemberId(),
			result.campusId(),
			result.campusName(),
			result.region(),
			result.campusRole(),
			result.status()
		);
	}
}
