package com.faithlog.campus.service.port;

public record CampusMemberLockScope(
	Long membershipId,
	Long campusId,
	Long userId
) {
}
