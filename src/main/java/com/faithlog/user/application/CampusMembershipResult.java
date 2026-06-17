package com.faithlog.user.application;

public record CampusMembershipResult(
	Long campusMemberId,
	Long campusId,
	String campusName,
	String region,
	String campusRole,
	String status
) {
}
