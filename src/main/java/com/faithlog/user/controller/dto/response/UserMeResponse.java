package com.faithlog.user.controller.dto.response;

import com.faithlog.user.service.result.UserMeResult;
import java.time.Instant;
import java.util.List;

public record UserMeResponse(
	Long id,
	String name,
	String email,
	String role,
	boolean isActive,
	Instant lastLoginAt,
	List<CampusMembershipResponse> campusMemberships
) {

	public static UserMeResponse from(UserMeResult result) {
		return new UserMeResponse(
			result.id(),
			result.name(),
			result.email(),
			result.role(),
			result.isActive(),
			result.lastLoginAt(),
			result.campusMemberships()
				.stream()
				.map(CampusMembershipResponse::from)
				.toList()
		);
	}
}
