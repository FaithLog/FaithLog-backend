package com.faithlog.campus.controller.dto.response;

import com.faithlog.campus.service.result.CampusCreateResult;

public record CampusCreateResponse(
	Long campusId,
	String name,
	String region,
	String description,
	String inviteCode,
	String myCampusRole,
	String membershipStatus
) {

	public static CampusCreateResponse from(CampusCreateResult result) {
		return new CampusCreateResponse(
			result.campusId(),
			result.name(),
			result.region(),
			result.description(),
			result.inviteCode(),
			result.myCampusRole(),
			result.membershipStatus()
		);
	}
}
