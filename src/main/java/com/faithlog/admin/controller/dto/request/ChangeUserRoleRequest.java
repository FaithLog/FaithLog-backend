package com.faithlog.admin.controller.dto.request;

import com.faithlog.admin.service.command.ChangeUserRoleCommand;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.domain.type.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeUserRoleRequest(
	@NotNull UserRole role
) {

	public ChangeUserRoleCommand toCommand(Long userId, AuthenticatedUser authenticatedUser) {
		return new ChangeUserRoleCommand(authenticatedUser.userId(), userId, role);
	}
}
