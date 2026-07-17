package com.faithlog.prayer.controller.dto.response;

import com.faithlog.prayer.service.result.PrayerGroupBoardResult;
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
