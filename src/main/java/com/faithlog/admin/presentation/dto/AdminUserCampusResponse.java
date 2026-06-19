package com.faithlog.admin.presentation.dto;

import com.faithlog.admin.application.AdminUserCampusResult;

public record AdminUserCampusResponse(
	Long membershipId,
	Long campusId,
	String campusName,
	String region,
	String campusRole,
	String status
) {

	public static AdminUserCampusResponse from(AdminUserCampusResult result) {
		return new AdminUserCampusResponse(
			result.membershipId(),
			result.campusId(),
			result.campusName(),
			result.region(),
			result.campusRole(),
			result.status()
		);
	}
}
