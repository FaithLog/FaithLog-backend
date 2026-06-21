package com.faithlog.prayer.presentation.dto;

import com.faithlog.prayer.application.PrayerGroupResult;
import java.util.List;

public record PrayerGroupResponse(
	Long groupId,
	Long seasonId,
	String name,
	int sortOrder,
	boolean active,
	List<PrayerGroupMemberResponse> members
) {

	public static PrayerGroupResponse from(PrayerGroupResult result) {
		return new PrayerGroupResponse(
			result.groupId(),
			result.seasonId(),
			result.name(),
			result.sortOrder(),
			result.active(),
			result.members().stream().map(PrayerGroupMemberResponse::from).toList()
		);
	}
}
