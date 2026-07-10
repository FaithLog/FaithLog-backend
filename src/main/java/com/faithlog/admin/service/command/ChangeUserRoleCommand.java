package com.faithlog.admin.service.command;

import com.faithlog.user.domain.UserRole;

public record ChangeUserRoleCommand(
	Long requesterId,
	Long userId,
	UserRole role
) {
}
