package com.faithlog.prayer.application;

import java.util.List;

public record ReplacePrayerGroupMembersCommand(
	Long groupId,
	Long requesterId,
	List<Long> userIds
) {
}
