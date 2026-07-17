package com.faithlog.prayer.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.service.command.CreatePrayerSeasonCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreatePrayerSeasonRequest(
	@NotBlank String name,
	@NotNull LocalDate startDate
) {

	public CreatePrayerSeasonCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new CreatePrayerSeasonCommand(campusId, authenticatedUser.userId(), name, startDate);
	}
}
