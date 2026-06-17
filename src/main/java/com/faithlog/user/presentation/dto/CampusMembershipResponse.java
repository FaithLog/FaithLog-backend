package com.faithlog.user.presentation.dto;

public record CampusMembershipResponse(
	Long campusMemberId,
	Long campusId,
	String campusName,
	String region,
	String campusRole,
	String status
) {
}
