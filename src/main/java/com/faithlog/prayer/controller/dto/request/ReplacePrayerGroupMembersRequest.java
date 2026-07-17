package com.faithlog.prayer.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.service.command.ReplacePrayerGroupMembersCommand;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplacePrayerGroupMembersRequest(
	@NotNull List<Long> userIds
) {

	public ReplacePrayerGroupMembersCommand toCommand(Long groupId, AuthenticatedUser authenticatedUser) {
		return new ReplacePrayerGroupMembersCommand(groupId, authenticatedUser.userId(), userIds);
	}
}
