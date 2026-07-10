package com.faithlog.admin.controller.dto.response;

import com.faithlog.admin.service.result.AdminUserCampusResult;

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
