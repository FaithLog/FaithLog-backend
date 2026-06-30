package com.faithlog.prayer.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.application.SaveMyPrayerSubmissionCommand;
import java.time.LocalDate;

public record SaveMyPrayerSubmissionRequest(
	String content
) {

	public SaveMyPrayerSubmissionCommand toCommand(Long campusId, LocalDate weekStartDate, AuthenticatedUser authenticatedUser) {
		return new SaveMyPrayerSubmissionCommand(campusId, weekStartDate, authenticatedUser.userId(), content);
	}
}
