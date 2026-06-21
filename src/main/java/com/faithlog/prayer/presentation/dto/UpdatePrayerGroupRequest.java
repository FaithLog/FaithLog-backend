package com.faithlog.prayer.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.application.UpdatePrayerGroupCommand;

public record UpdatePrayerGroupRequest(
	String name,
	Integer sortOrder,
	Boolean isActive
) {

	public UpdatePrayerGroupCommand toCommand(Long groupId, AuthenticatedUser authenticatedUser) {
		return new UpdatePrayerGroupCommand(groupId, authenticatedUser.userId(), name, sortOrder, isActive);
	}
}
