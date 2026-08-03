package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.domain.entity.AnnouncementNotificationOutbox;
import com.faithlog.announcement.service.port.AnnouncementNotificationOutboxRepositoryPort;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.notification.service.command.AutomaticNotificationRequestCommand;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnnouncementNotificationOutboxProcessorTest {

	@Mock AnnouncementNotificationOutboxRepositoryPort outboxRepository;
	@Mock CampusMemberRepository campusMemberRepository;
	@Mock NotificationRequestCommandService notificationRequestCommandService;

	@Test
	void sends_to_active_members_except_the_author_with_the_exact_payload_and_copy() {
		AnnouncementNotificationOutbox outbox = outbox();
		when(outboxRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(outbox));
		when(campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(7L,
			com.faithlog.campus.domain.type.CampusMemberStatus.ACTIVE))
			.thenReturn(List.of(member(1L, 11L), member(2L, 12L), member(3L, 13L)));

		new AnnouncementNotificationOutboxProcessor(
			outboxRepository, campusMemberRepository, notificationRequestCommandService
		).process(10L);

		ArgumentCaptor<AutomaticNotificationRequestCommand> command =
			ArgumentCaptor.forClass(AutomaticNotificationRequestCommand.class);
		verify(notificationRequestCommandService).requestRequiredAutomaticNotification(command.capture());
		assertThat(command.getValue().notificationType()).isEqualTo(NotificationType.ANNOUNCEMENT_PUBLISHED);
		assertThat(command.getValue().targetUserIds()).containsExactly(12L, 13L);
		assertThat(command.getValue().title()).isEqualTo("새 공지가 등록되었어요");
		assertThat(command.getValue().body()).isEqualTo("[일반] 예배 안내");
		assertThat(command.getValue().data()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
			"eventType", "ANNOUNCEMENT_PUBLISHED",
			"campusId", "7",
			"announcementId", "99",
			"categoryId", "3"
		));
		assertThat(outbox.isProcessed()).isTrue();
	}

	@Test
	void processed_outbox_is_idempotent() {
		AnnouncementNotificationOutbox outbox = outbox();
		outbox.markProcessed();
		when(outboxRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(outbox));

		boolean processed = new AnnouncementNotificationOutboxProcessor(
			outboxRepository, campusMemberRepository, notificationRequestCommandService
		).process(10L);

		assertThat(processed).isFalse();
		verify(notificationRequestCommandService, never()).requestRequiredAutomaticNotification(any());
	}

	@Test
	void required_notification_failure_keeps_outbox_pending_for_retry() {
		AnnouncementNotificationOutbox outbox = outbox();
		when(outboxRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(outbox));
		when(campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(7L,
			com.faithlog.campus.domain.type.CampusMemberStatus.ACTIVE))
			.thenReturn(List.of(member(1L, 11L), member(2L, 12L)));
		org.mockito.Mockito.doThrow(new com.faithlog.global.exception.BusinessException(
			com.faithlog.global.exception.ErrorCode.NOTIFICATION_REDIS_UNAVAILABLE))
			.when(notificationRequestCommandService).requestRequiredAutomaticNotification(any());

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AnnouncementNotificationOutboxProcessor(
			outboxRepository, campusMemberRepository, notificationRequestCommandService).process(10L))
			.isInstanceOf(com.faithlog.global.exception.BusinessException.class);

		assertThat(outbox.isProcessed()).isFalse();
	}

	private AnnouncementNotificationOutbox outbox() {
		AnnouncementNotificationOutbox outbox = AnnouncementNotificationOutbox.create(
			99L, 7L, 3L, 11L, "일반", "예배 안내", Instant.parse("2026-08-03T00:00:00Z")
		);
		org.springframework.test.util.ReflectionTestUtils.setField(outbox, "id", 10L);
		return outbox;
	}

	private CampusMember member(Long id, Long userId) {
		CampusMember member = CampusMember.createMember(7L, userId);
		org.springframework.test.util.ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
