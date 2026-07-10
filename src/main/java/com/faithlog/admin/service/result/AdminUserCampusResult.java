package com.faithlog.admin.service.result;

import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusMember;

public record AdminUserCampusResult(
	Long membershipId,
	Long campusId,
	String campusName,
	String region,
	String campusRole,
	String status
) {

	public static AdminUserCampusResult of(CampusMember member, Campus campus) {
		return new AdminUserCampusResult(
			member.id(),
			campus.id(),
			campus.name(),
			campus.region(),
			member.campusRole().name(),
			member.status().name()
		);
	}
}
