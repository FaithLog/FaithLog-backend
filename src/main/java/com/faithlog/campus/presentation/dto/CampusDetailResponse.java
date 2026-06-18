package com.faithlog.campus.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.faithlog.campus.application.CampusDetailResult;

public record CampusDetailResponse(
	Long campusId,
	String name,
	String region,
	String description,
	boolean isActive,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	String inviteCode,
	String myCampusRole,
	String membershipStatus
) {

	public static CampusDetailResponse from(CampusDetailResult result) {
		return new CampusDetailResponse(
			result.campusId(),
			result.name(),
			result.region(),
			result.description(),
			result.isActive(),
			result.inviteCode(),
			result.myCampusRole(),
			result.membershipStatus()
		);
	}
}
