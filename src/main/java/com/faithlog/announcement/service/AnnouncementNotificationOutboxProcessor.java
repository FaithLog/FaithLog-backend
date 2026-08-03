package com.faithlog.announcement.service;

import com.faithlog.announcement.domain.entity.AnnouncementNotificationOutbox;
import com.faithlog.announcement.service.port.AnnouncementNotificationOutboxRepositoryPort;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.notification.service.command.AutomaticNotificationRequestCommand;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementNotificationOutboxProcessor {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final String PUSH_TITLE = "새 공지가 등록되었어요";

	private final AnnouncementNotificationOutboxRepositoryPort outboxRepository;
	private final CampusMemberRepository campusMemberRepository;
	private final NotificationRequestCommandService notificationRequestCommandService;

	public AnnouncementNotificationOutboxProcessor(
		AnnouncementNotificationOutboxRepositoryPort outboxRepository,
		CampusMemberRepository campusMemberRepository,
		NotificationRequestCommandService notificationRequestCommandService
	) {
		this.outboxRepository = outboxRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.notificationRequestCommandService = notificationRequestCommandService;
	}

	@Transactional
	public boolean process(Long outboxId) {
		AnnouncementNotificationOutbox outbox = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
		if (outbox == null || outbox.isProcessed()) {
			return false;
		}
		var targets = campusMemberRepository
			.findByCampusIdAndStatusOrderByIdAsc(outbox.campusId(), CampusMemberStatus.ACTIVE)
			.stream()
			.map(CampusMember::userId)
			.filter(userId -> !userId.equals(outbox.authorId()))
			.distinct()
			.toList();
		notificationRequestCommandService.requestAutomaticNotification(new AutomaticNotificationRequestCommand(
			outbox.campusId(),
			NotificationType.ANNOUNCEMENT_PUBLISHED,
			null,
			outbox.announcementId(),
			targets,
			LocalDate.ofInstant(outbox.publishedAt(), SEOUL_ZONE),
			"announcement:" + outbox.announcementId(),
			PUSH_TITLE,
			"[" + outbox.categoryName() + "] " + outbox.announcementTitle(),
			Map.of(
				"eventType", "ANNOUNCEMENT_PUBLISHED",
				"campusId", outbox.campusId().toString(),
				"announcementId", outbox.announcementId().toString(),
				"categoryId", outbox.categoryId().toString()
			)
		));
		outbox.markProcessed();
		return true;
	}
}
