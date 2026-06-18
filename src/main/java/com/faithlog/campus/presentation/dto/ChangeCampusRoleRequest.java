package com.faithlog.campus.presentation.dto;

import com.faithlog.campus.application.ChangeCampusRoleCommand;
import com.faithlog.campus.domain.CampusRole;
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
