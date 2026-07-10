package com.faithlog.admin.service.command;

import com.faithlog.user.domain.type.UserRole;

public record ChangeUserRoleCommand(
	Long requesterId,
	Long userId,
	UserRole role
) {
}
