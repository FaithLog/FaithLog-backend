package com.faithlog.devotion.application;

import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusMember;

public record MissingDevotionMemberResult(
	Long userId,
	String name,
	String email,
	Long campusMemberId,
	String campusName,
	String region
) {

	public static MissingDevotionMemberResult of(CampusMember member, CampusUserLookupResult user, Campus campus) {
		return new MissingDevotionMemberResult(
			member.userId(),
			user.name(),
			user.email(),
			member.id(),
			campus.name(),
			campus.region()
		);
	}
}
