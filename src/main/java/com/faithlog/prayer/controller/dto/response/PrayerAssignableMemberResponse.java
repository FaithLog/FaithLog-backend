package com.faithlog.prayer.controller.dto.response;

import com.faithlog.prayer.service.result.PrayerAssignableMemberResult;

public record PrayerAssignableMemberResponse(
	Long userId,
	String name,
	String email,
	Long assignedGroupId,
	String assignedGroupName,
	boolean assignable
) {

	public static PrayerAssignableMemberResponse from(PrayerAssignableMemberResult result) {
		return new PrayerAssignableMemberResponse(
			result.userId(),
			result.name(),
			result.email(),
			result.assignedGroupId(),
			result.assignedGroupName(),
			result.assignable()
		);
	}
}
