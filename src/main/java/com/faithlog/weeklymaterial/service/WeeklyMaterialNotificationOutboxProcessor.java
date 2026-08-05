package com.faithlog.weeklymaterial.service;

import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.notification.service.command.AutomaticNotificationRequestCommand;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRecipientPort;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyMaterialNotificationOutboxProcessor {
	private static final String TITLE = "새 주일설교 나눔지가 등록되었어요";
	private final WeeklyMaterialNotificationOutboxRepositoryPort outboxes;
	private final WeeklyMaterialRecipientPort recipients;
	private final NotificationRequestCommandService notifications;
	private final Clock clock;

	public WeeklyMaterialNotificationOutboxProcessor(WeeklyMaterialNotificationOutboxRepositoryPort outboxes,
		WeeklyMaterialRecipientPort recipients, NotificationRequestCommandService notifications, Clock clock) {
		this.outboxes = outboxes;
		this.recipients = recipients;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional
	public boolean process(Long outboxId) {
		var outbox = outboxes.findByIdForUpdate(outboxId).orElse(null);
		if (outbox == null || outbox.isProcessed()) return false;
		var targets = recipients.findActiveMemberUserIds(outbox.campusId()).stream()
			.filter(id -> !id.equals(outbox.uploaderId())).distinct().toList();
		notifications.requestRequiredAutomaticNotification(new AutomaticNotificationRequestCommand(
			outbox.campusId(), NotificationType.WEEKLY_SHARING_SHEET_PUBLISHED, outbox.weekStartDate(),
			outbox.weeklyMaterialId(), targets, outbox.weekStartDate(),
			"weekly-sharing-sheet:" + outbox.campusId() + ":" + outbox.weekStartDate(), TITLE,
			outbox.weekStartDate() + " 주차 주일설교 나눔지를 확인해 주세요",
			Map.of("eventType", "WEEKLY_SHARING_SHEET_PUBLISHED",
				"campusId", outbox.campusId().toString(),
				"weeklyMaterialId", outbox.weeklyMaterialId().toString(),
				"weekStartDate", outbox.weekStartDate().toString())));
		outbox.markProcessed(clock.instant());
		return true;
	}
}
