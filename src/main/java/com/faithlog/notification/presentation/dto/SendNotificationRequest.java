package com.faithlog.notification.presentation.dto;

import com.faithlog.notification.application.SendNotificationCommand;
import com.faithlog.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record SendNotificationRequest(
	@NotNull
	NotificationType notificationType,

	List<Long> targetUserIds,

	LocalDate targetWeekStartDate,

	Long targetId,

	@NotBlank
	@Size(max = 200)
	String title,

	@NotBlank
	String body
) {

	public SendNotificationCommand toCommand(Long campusId, Long requesterId) {
		return new SendNotificationCommand(
			campusId,
			requesterId,
			notificationType,
			targetUserIds,
			targetWeekStartDate,
			targetId,
			title,
			body
		);
	}
}
