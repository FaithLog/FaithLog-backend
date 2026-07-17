package com.faithlog.campus.controller.dto.request;

import com.faithlog.campus.service.command.ChangeCampusRoleCommand;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;

public record ChangeCampusRoleRequest(
	@NotNull CampusRole campusRole
) {

	public ChangeCampusRoleCommand toCommand(
		Long campusId,
		Long campusMemberId,
		AuthenticatedUser authenticatedUser
	) {
		return new ChangeCampusRoleCommand(campusId, campusMemberId, authenticatedUser.userId(), campusRole);
	}
}
