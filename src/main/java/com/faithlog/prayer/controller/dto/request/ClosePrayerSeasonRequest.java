package com.faithlog.prayer.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.service.command.ClosePrayerSeasonCommand;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ClosePrayerSeasonRequest(
	@NotNull LocalDate endDate
) {

	public ClosePrayerSeasonCommand toCommand(Long seasonId, AuthenticatedUser authenticatedUser) {
		return new ClosePrayerSeasonCommand(seasonId, authenticatedUser.userId(), endDate);
	}
}
