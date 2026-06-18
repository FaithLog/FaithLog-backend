package com.faithlog.campus.application;

import com.faithlog.campus.domain.CampusRole;

public record ChangeCampusRoleCommand(
	Long campusId,
	Long campusMemberId,
	Long requesterId,
	CampusRole campusRole
) {
}
