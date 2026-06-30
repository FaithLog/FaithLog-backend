package com.faithlog.prayer.presentation.dto;

import com.faithlog.prayer.application.PrayerGroupBoardResult;
import java.util.List;

public record PrayerGroupBoardResponse(
	Long groupId,
	Long seasonId,
	String groupName,
	int sortOrder,
	List<PrayerMemberSubmissionResponse> members
) {

	public static PrayerGroupBoardResponse from(PrayerGroupBoardResult result) {
		return new PrayerGroupBoardResponse(
			result.groupId(),
			result.seasonId(),
			result.groupName(),
			result.sortOrder(),
			result.members().stream().map(PrayerMemberSubmissionResponse::from).toList()
		);
	}
}
