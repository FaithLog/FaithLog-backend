package com.faithlog.prayer.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.application.PrayerSubmissionCommand;
import com.faithlog.prayer.application.SavePrayerSubmissionsCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record SavePrayerSubmissionsRequest(
	@Valid @NotNull List<PrayerSubmissionRequest> submissions
) {

	public SavePrayerSubmissionsCommand toCommand(Long campusId, LocalDate weekStartDate, AuthenticatedUser authenticatedUser) {
		return new SavePrayerSubmissionsCommand(
			campusId,
			weekStartDate,
			authenticatedUser.userId(),
			submissions.stream()
				.map(PrayerSubmissionRequest::toCommand)
				.toList()
		);
	}

	public record PrayerSubmissionRequest(
		@NotNull Long userId,
		String content,
		@NotNull Integer version
	) {

		PrayerSubmissionCommand toCommand() {
			return new PrayerSubmissionCommand(userId, content, version);
		}
	}
}
