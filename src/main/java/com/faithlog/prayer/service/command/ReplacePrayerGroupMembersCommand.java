package com.faithlog.prayer.service.command;

import java.util.List;

public record ReplacePrayerGroupMembersCommand(
	Long groupId,
	Long requesterId,
	List<Long> userIds
) {
}
