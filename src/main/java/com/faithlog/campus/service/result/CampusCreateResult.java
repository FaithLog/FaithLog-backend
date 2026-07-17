package com.faithlog.campus.service.result;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;

public record CampusCreateResult(
	Long campusId,
	String name,
	String region,
	String description,
	String inviteCode,
	String myCampusRole,
	String membershipStatus
) {

	public static CampusCreateResult of(Campus campus, CampusMember member) {
		return new CampusCreateResult(
			campus.id(),
			campus.name(),
			campus.region(),
			campus.description(),
			campus.inviteCode(),
			member.campusRole().name(),
			member.status().name()
		);
	}
}
