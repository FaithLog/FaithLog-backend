package com.faithlog.poll.service;

import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.poll.domain.type.PollType;

final class PollOpenNotificationTypeMapper {

	private PollOpenNotificationTypeMapper() {
	}

	static NotificationType map(PollType pollType) {
		return switch (pollType) {
			case WED_SERVICE -> NotificationType.WED_POLL_OPEN;
			case SATURDAY_LEADER -> NotificationType.SATURDAY_POLL_OPEN;
			case COFFEE -> NotificationType.COFFEE_POLL_OPEN;
			case MEAL -> NotificationType.MEAL_POLL_OPEN;
			case CUSTOM -> NotificationType.CUSTOM_POLL_OPEN;
		};
	}
}
