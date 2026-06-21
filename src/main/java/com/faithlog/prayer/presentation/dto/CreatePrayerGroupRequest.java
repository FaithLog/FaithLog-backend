package com.faithlog.prayer.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.application.CreatePrayerGroupCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePrayerGroupRequest(
	@NotBlank String name,
	@NotNull Integer sortOrder
) {

	public CreatePrayerGroupCommand toCommand(Long seasonId, AuthenticatedUser authenticatedUser) {
		return new CreatePrayerGroupCommand(seasonId, authenticatedUser.userId(), name, sortOrder);
	}
}
