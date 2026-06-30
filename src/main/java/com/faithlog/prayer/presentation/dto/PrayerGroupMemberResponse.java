package com.faithlog.prayer.presentation.dto;

import com.faithlog.prayer.application.PrayerGroupMemberResult;

public record PrayerGroupMemberResponse(
	Long userId,
	String name,
	String email
) {

	public static PrayerGroupMemberResponse from(PrayerGroupMemberResult result) {
		return new PrayerGroupMemberResponse(result.userId(), result.name(), result.email());
	}
}
