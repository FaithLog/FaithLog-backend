package com.faithlog.admin.presentation.dto;

import com.faithlog.admin.application.ChangeUserRoleCommand;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequest(
	@NotNull UserRole role
) {

	public ChangeUserRoleCommand toCommand(Long userId, AuthenticatedUser authenticatedUser) {
		return new ChangeUserRoleCommand(authenticatedUser.userId(), userId, role);
	}
}
