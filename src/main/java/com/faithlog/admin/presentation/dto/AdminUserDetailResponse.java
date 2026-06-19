package com.faithlog.admin.presentation.dto;

import com.faithlog.admin.application.AdminUserResult;
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
