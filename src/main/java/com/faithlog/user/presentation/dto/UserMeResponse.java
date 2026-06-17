package com.faithlog.user.presentation.dto;

import com.faithlog.user.domain.User;
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

	public static UserMeResponse from(User user) {
		return new UserMeResponse(
			user.id(),
			user.name(),
			user.email(),
			user.role().name(),
			user.isActive(),
			user.lastLoginAt(),
			List.of()
		);
	}
}
