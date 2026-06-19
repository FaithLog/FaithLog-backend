package com.faithlog.admin.presentation.dto;

import com.faithlog.admin.application.AdminCampusResult;

public record AdminCampusResponse(
	Long campusId,
	String name,
	String region,
	boolean isActive,
	String status,
	long memberCount,
	long adminCount
) {

	public static AdminCampusResponse from(AdminCampusResult result) {
		return new AdminCampusResponse(
			result.campusId(),
			result.name(),
			result.region(),
			result.isActive(),
			result.status(),
			result.memberCount(),
			result.adminCount()
		);
	}
}
