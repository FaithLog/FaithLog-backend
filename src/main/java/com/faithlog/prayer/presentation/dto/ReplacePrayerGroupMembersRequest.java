package com.faithlog.prayer.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.application.ReplacePrayerGroupMembersCommand;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplacePrayerGroupMembersRequest(
	@NotNull List<Long> userIds
) {

	public ReplacePrayerGroupMembersCommand toCommand(Long groupId, AuthenticatedUser authenticatedUser) {
		return new ReplacePrayerGroupMembersCommand(groupId, authenticatedUser.userId(), userIds);
	}
}
