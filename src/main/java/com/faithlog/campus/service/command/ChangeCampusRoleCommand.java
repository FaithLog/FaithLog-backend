package com.faithlog.campus.service.command;

import com.faithlog.campus.domain.type.CampusRole;

public record ChangeCampusRoleCommand(
	Long campusId,
	Long campusMemberId,
	Long requesterId,
	CampusRole campusRole
) {
}
