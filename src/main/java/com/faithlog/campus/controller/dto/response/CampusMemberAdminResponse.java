package com.faithlog.campus.controller.dto.response;

import com.faithlog.campus.service.result.AdminCampusMemberResult;

public record CampusMemberAdminResponse(
	Long membershipId,
	Long campusId,
	Long userId,
	String name,
	String email,
	String campusRole,
	String status
) {

	public static CampusMemberAdminResponse from(AdminCampusMemberResult result) {
		return new CampusMemberAdminResponse(
			result.membershipId(),
			result.campusId(),
			result.userId(),
			result.name(),
			result.email(),
			result.campusRole(),
			result.status()
		);
	}
}
