package com.faithlog.admin.service.result;

import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.domain.type.CampusRole;

public record AdminUserCampusRow(
	Long userId,
	Long membershipId,
	Long resolvedCampusId,
	String campusName,
	String region,
	CampusRole campusRole,
	CampusMemberStatus status
) {
}
