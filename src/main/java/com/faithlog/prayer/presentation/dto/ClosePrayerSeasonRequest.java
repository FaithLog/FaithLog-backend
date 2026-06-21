package com.faithlog.prayer.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.application.ClosePrayerSeasonCommand;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ClosePrayerSeasonRequest(
	@NotNull LocalDate endDate
) {

	public ClosePrayerSeasonCommand toCommand(Long seasonId, AuthenticatedUser authenticatedUser) {
		return new ClosePrayerSeasonCommand(seasonId, authenticatedUser.userId(), endDate);
	}
}
