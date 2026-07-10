package com.faithlog.user.service.result;

public record CampusMembershipResult(
	Long campusMemberId,
	Long campusId,
	String campusName,
	String region,
	String campusRole,
	String status
) {
}
