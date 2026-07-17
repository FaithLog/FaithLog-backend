package com.faithlog.admin.controller.dto.response;

import com.faithlog.admin.service.result.AdminUserResult;
import java.util.List;

public record AdminUserSummaryResponse(
	Long userId,
	String name,
	String email,
	String role,
	int campusCount,
	List<AdminUserCampusResponse> campuses
) {

	public static AdminUserSummaryResponse from(AdminUserResult result) {
		return new AdminUserSummaryResponse(
			result.userId(),
			result.name(),
			result.email(),
			result.role(),
			result.campusCount(),
			result.campuses().stream().map(AdminUserCampusResponse::from).toList()
		);
	}
}
