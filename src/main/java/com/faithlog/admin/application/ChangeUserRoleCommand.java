package com.faithlog.admin.application;

import com.faithlog.user.domain.UserRole;

public record ChangeUserRoleCommand(
	Long requesterId,
	Long userId,
	UserRole role
) {
}
