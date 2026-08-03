package com.faithlog.notification.service.command;

import com.faithlog.notification.domain.type.NotificationType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AutomaticNotificationRequestCommand(
	Long campusId,
	NotificationType notificationType,
	LocalDate targetWeekStartDate,
	Long targetId,
	List<Long> targetUserIds,
	LocalDate businessDate,
	String scopeId,
	String title,
	String body,
	Map<String, String> data
) {
	public AutomaticNotificationRequestCommand(
		Long campusId,
		NotificationType notificationType,
		LocalDate targetWeekStartDate,
		Long targetId,
		List<Long> targetUserIds,
		LocalDate businessDate,
		String scopeId,
		String title,
		String body
	) {
		this(campusId, notificationType, targetWeekStartDate, targetId, targetUserIds, businessDate, scopeId, title, body,
			Map.of());
	}

	public AutomaticNotificationRequestCommand {
		data = data == null || data.isEmpty() ? Map.of() : Map.copyOf(data);
	}
}
