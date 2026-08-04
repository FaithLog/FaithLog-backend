package com.faithlog.poll.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.notification.service.command.AutomaticNotificationRequestCommand;
import com.faithlog.poll.infrastructure.repository.PollNotificationOutboxRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PollNotificationOutboxProcessor {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final String PUSH_TITLE = "새 투표가 등록되었어요";

	private final PollNotificationOutboxRepository outboxes;
	private final CampusMemberRepository campusMembers;
	private final NotificationRequestCommandService notifications;
	private final Clock clock;

	public PollNotificationOutboxProcessor(
		PollNotificationOutboxRepository outboxes,
		CampusMemberRepository campusMembers,
		NotificationRequestCommandService notifications,
		Clock clock
	) {
		this.outboxes = outboxes;
		this.campusMembers = campusMembers;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional
	public boolean process(Long outboxId) {
		var outbox = outboxes.findByIdForUpdate(outboxId).orElse(null);
		if (outbox == null || outbox.isProcessed()) {
			return false;
		}
		var targets = campusMembers
			.findByCampusIdAndStatusOrderByIdAsc(outbox.campusId(), CampusMemberStatus.ACTIVE)
			.stream()
			.map(CampusMember::userId)
			.filter(userId -> outbox.creatorId() == null || !userId.equals(outbox.creatorId()))
			.distinct()
			.toList();
		notifications.requestRequiredAutomaticNotification(new AutomaticNotificationRequestCommand(
			outbox.campusId(),
			PollOpenNotificationTypeMapper.map(outbox.pollType()),
			null,
			outbox.pollId(),
			targets,
			LocalDate.ofInstant(outbox.openedAt(), SEOUL_ZONE),
			"poll-open:" + outbox.pollId(),
			PUSH_TITLE,
			outbox.pollTitle() + " 투표에 참여해 주세요.",
			Map.of(
				"eventType", "POLL_OPEN",
				"campusId", outbox.campusId().toString(),
				"pollId", outbox.pollId().toString()
			)
		));
		outbox.markProcessed(clock.instant());
		return true;
	}
}
