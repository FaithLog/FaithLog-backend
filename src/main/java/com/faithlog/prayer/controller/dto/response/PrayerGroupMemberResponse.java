package com.faithlog.prayer.controller.dto.response;

import com.faithlog.prayer.service.result.PrayerGroupMemberResult;

public record PrayerGroupMemberResponse(
	Long userId,
	String name,
	String email
) {

	public static PrayerGroupMemberResponse from(PrayerGroupMemberResult result) {
		return new PrayerGroupMemberResponse(result.userId(), result.name(), result.email());
	}
}
