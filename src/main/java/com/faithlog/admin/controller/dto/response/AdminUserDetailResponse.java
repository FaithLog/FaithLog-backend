package com.faithlog.admin.controller.dto.response;

import com.faithlog.admin.service.result.AdminUserResult;
import java.util.List;

public record AdminUserDetailResponse(
	Long userId,
	String name,
	String email,
	String role,
	boolean isActive,
	List<AdminUserCampusResponse> campuses
) {

	public static AdminUserDetailResponse from(AdminUserResult result) {
		return new AdminUserDetailResponse(
			result.userId(),
			result.name(),
			result.email(),
			result.role(),
			result.isActive(),
			result.campuses().stream().map(AdminUserCampusResponse::from).toList()
		);
	}
}
