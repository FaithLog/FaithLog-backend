package com.faithlog.user.service.result;

import com.faithlog.user.domain.entity.User;
import java.time.Instant;
import java.util.List;

public record UserMeResult(
	Long id,
	String name,
	String email,
	String role,
	boolean isActive,
	Instant lastLoginAt,
	List<CampusMembershipResult> campusMemberships
) {

	public static UserMeResult from(User user) {
		return from(user, List.of());
	}

	public static UserMeResult from(User user, List<CampusMembershipResult> campusMemberships) {
		return new UserMeResult(
			user.id(),
			user.name(),
			user.email(),
			user.role().name(),
			user.isActive(),
			user.lastLoginAt(),
			campusMemberships
		);
	}
}
