package com.faithlog.prayer.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.service.command.UpdatePrayerGroupCommand;

public record UpdatePrayerGroupRequest(
	String name,
	Integer sortOrder,
	Boolean isActive
) {

	public UpdatePrayerGroupCommand toCommand(Long groupId, AuthenticatedUser authenticatedUser) {
		return new UpdatePrayerGroupCommand(groupId, authenticatedUser.userId(), name, sortOrder, isActive);
	}
}
