package com.faithlog.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.notification.service.command.AutomaticNotificationRequestCommand;
import com.faithlog.poll.domain.entity.PollNotificationOutbox;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.PollNotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PollNotificationOutboxProcessorTest {

	private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
	@Mock private PollNotificationOutboxRepository outboxes;
	@Mock private CampusMemberRepository campusMembers;
	@Mock private NotificationRequestCommandService notifications;

	@Test
	void sends_exact_safe_payload_to_active_members_except_creator() {
		PollNotificationOutbox outbox = outbox(PollType.MEAL);
		when(outboxes.findByIdForUpdate(10L)).thenReturn(Optional.of(outbox));
		when(campusMembers.findByCampusIdAndStatusOrderByIdAsc(7L, CampusMemberStatus.ACTIVE))
			.thenReturn(List.of(member(1L, 11L), member(2L, 12L), member(3L, 13L)));

		new PollNotificationOutboxProcessor(outboxes, campusMembers, notifications,
			Clock.fixed(NOW, ZoneOffset.UTC)).process(10L);

		ArgumentCaptor<AutomaticNotificationRequestCommand> command =
			ArgumentCaptor.forClass(AutomaticNotificationRequestCommand.class);
		verify(notifications).requestRequiredAutomaticNotification(command.capture());
		assertThat(command.getValue().notificationType()).isEqualTo(NotificationType.MEAL_POLL_OPEN);
		assertThat(command.getValue().targetUserIds()).containsExactly(12L, 13L);
		assertThat(command.getValue().title()).isEqualTo("새 투표가 등록되었어요");
		assertThat(command.getValue().body()).isEqualTo("점심 메뉴 투표에 참여해 주세요.");
		assertThat(command.getValue().data()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
			"eventType", "POLL_OPEN", "campusId", "7", "pollId", "99"));
		assertThat(command.getValue().data()).doesNotContainKeys("notice", "imageUrl", "optionIds");
		assertThat(outbox.isProcessed()).isTrue();
	}

	@Test
	void downstream_failure_keeps_outbox_pending_for_retry() {
		PollNotificationOutbox outbox = outbox(PollType.CUSTOM);
		when(outboxes.findByIdForUpdate(10L)).thenReturn(Optional.of(outbox));
		when(campusMembers.findByCampusIdAndStatusOrderByIdAsc(7L, CampusMemberStatus.ACTIVE))
			.thenReturn(List.of(member(1L, 11L), member(2L, 12L)));
		org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.NOTIFICATION_REDIS_UNAVAILABLE))
			.when(notifications).requestRequiredAutomaticNotification(any());

		assertThatThrownBy(() -> new PollNotificationOutboxProcessor(
			outboxes, campusMembers, notifications, Clock.fixed(NOW, ZoneOffset.UTC)).process(10L))
			.isInstanceOf(BusinessException.class);

		assertThat(outbox.isProcessed()).isFalse();
	}

	@Test
	void processed_outbox_does_not_send_again() {
		PollNotificationOutbox outbox = outbox(PollType.WED_SERVICE);
		outbox.markProcessed(NOW);
		when(outboxes.findByIdForUpdate(10L)).thenReturn(Optional.of(outbox));

		boolean processed = new PollNotificationOutboxProcessor(
			outboxes, campusMembers, notifications, Clock.fixed(NOW, ZoneOffset.UTC)).process(10L);

		assertThat(processed).isFalse();
		verify(notifications, never()).requestRequiredAutomaticNotification(any());
	}

	@Test
	void maps_all_poll_types_to_open_notification_types() {
		assertThat(java.util.Arrays.stream(PollType.values()).map(PollOpenNotificationTypeMapper::map).toList())
			.containsExactly(
				NotificationType.WED_POLL_OPEN,
				NotificationType.SATURDAY_POLL_OPEN,
				NotificationType.COFFEE_POLL_OPEN,
				NotificationType.MEAL_POLL_OPEN,
				NotificationType.CUSTOM_POLL_OPEN);
	}

	private PollNotificationOutbox outbox(PollType type) {
		PollNotificationOutbox outbox = PollNotificationOutbox.create(
			99L, 7L, 11L, type, "점심 메뉴", NOW.minusSeconds(60));
		org.springframework.test.util.ReflectionTestUtils.setField(outbox, "id", 10L);
		return outbox;
	}

	private CampusMember member(Long id, Long userId) {
		CampusMember member = CampusMember.createMember(7L, userId);
		org.springframework.test.util.ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
