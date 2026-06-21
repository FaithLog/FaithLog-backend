package com.faithlog.prayer.presentation.dto;

import com.faithlog.prayer.application.PrayerGroupMemberResult;

public record PrayerGroupMemberResponse(
	Long userId,
	String name
) {

	public static PrayerGroupMemberResponse from(PrayerGroupMemberResult result) {
		return new PrayerGroupMemberResponse(result.userId(), result.name());
	}
}
