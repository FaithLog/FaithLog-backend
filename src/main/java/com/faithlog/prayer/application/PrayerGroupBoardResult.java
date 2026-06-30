package com.faithlog.prayer.application;

import java.util.List;

public record PrayerGroupBoardResult(
	Long groupId,
	Long seasonId,
	String groupName,
	int sortOrder,
	List<PrayerMemberSubmissionResult> members
) {
}
