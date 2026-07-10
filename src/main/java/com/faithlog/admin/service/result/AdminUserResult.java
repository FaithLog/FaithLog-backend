package com.faithlog.admin.service.result;

import com.faithlog.user.domain.entity.User;
import java.util.List;

public record AdminUserResult(
	Long userId,
	String name,
	String email,
	String role,
	boolean isActive,
	int campusCount,
	List<AdminUserCampusResult> campuses
) {

	public static AdminUserResult of(User user, List<AdminUserCampusResult> campuses) {
		return new AdminUserResult(
			user.id(),
			user.name(),
			user.email(),
			user.role().name(),
			user.isActive(),
			campuses.size(),
			campuses
		);
	}
}
