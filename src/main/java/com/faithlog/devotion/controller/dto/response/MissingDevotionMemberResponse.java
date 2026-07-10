package com.faithlog.devotion.controller.dto.response;

import com.faithlog.devotion.service.result.MissingDevotionMemberResult;

public record MissingDevotionMemberResponse(
	Long userId,
	String name,
	String email,
	Long campusMemberId,
	String campusName,
	String region
) {

	public static MissingDevotionMemberResponse from(MissingDevotionMemberResult result) {
		return new MissingDevotionMemberResponse(
			result.userId(),
			result.name(),
			result.email(),
			result.campusMemberId(),
			result.campusName(),
			result.region()
		);
	}
}
