package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.service.port.AnnouncementNotificationOutboxRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AnnouncementRetentionServiceTest {
	private final AnnouncementRepositoryPort announcements = mock(AnnouncementRepositoryPort.class);
	private final AnnouncementImageAttachmentService images = mock(AnnouncementImageAttachmentService.class);
	private final AnnouncementDocumentAttachmentService documents = mock(AnnouncementDocumentAttachmentService.class);
	private final AnnouncementNotificationOutboxRepositoryPort outboxes =
		mock(AnnouncementNotificationOutboxRepositoryPort.class);
	private final AnnouncementRetentionService service =
		new AnnouncementRetentionService(announcements, images, documents);

	@Test
	void physicallyDeletesPublishedAnnouncementAtSeoulCalendarMonthBoundaryAndPreservesOutbox() {
		Announcement announcement = published(Instant.parse("2026-05-04T20:00:00Z"));
		when(announcements.findByIdForUpdate(10L)).thenReturn(Optional.of(announcement));

		assertThat(service.deleteIfDue(10L, Instant.parse("2026-08-04T15:00:00Z"))).isTrue();

		verify(images).orphanAll(10L, 1L);
		verify(documents).orphanAll(10L, 1L);
		verify(announcements).delete(announcement);
		verify(outboxes, never()).deleteByAnnouncementId(10L);
	}

	@Test
	void physicallyDeletesArchivedAnnouncementUsingOriginalPublishedAt() {
		Announcement announcement = published(Instant.parse("2026-05-04T20:00:00Z"));
		announcement.archive();
		when(announcements.findByIdForUpdate(10L)).thenReturn(Optional.of(announcement));

		assertThat(service.deleteIfDue(10L, Instant.parse("2026-08-04T15:00:00Z"))).isTrue();

		verify(announcements).delete(announcement);
	}

	@Test
	void doesNotDeleteBeforeSeoulMidnightBoundary() {
		Announcement announcement = published(Instant.parse("2026-05-04T20:00:00Z"));
		when(announcements.findByIdForUpdate(10L)).thenReturn(Optional.of(announcement));

		assertThat(service.deleteIfDue(10L, Instant.parse("2026-08-04T14:59:59Z"))).isFalse();

		verify(images, never()).orphanAll(10L, 1L);
		verify(documents, never()).orphanAll(10L, 1L);
		verify(announcements, never()).delete(announcement);
	}

	@Test
	void neverDeletesUnpublishedScheduledAnnouncement() {
		Announcement announcement = Announcement.createScheduled(1L, 2L, 3L, "제목", "내용", false,
			Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"));
		ReflectionTestUtils.setField(announcement, "id", 10L);
		when(announcements.findByIdForUpdate(10L)).thenReturn(Optional.of(announcement));

		assertThat(service.deleteIfDue(10L, Instant.parse("2027-01-01T00:00:00Z"))).isFalse();

		verify(announcements, never()).delete(announcement);
	}

	private static Announcement published(Instant publishedAt) {
		Announcement announcement = Announcement.createPublished(1L, 2L, 3L, "제목", "내용", false, publishedAt);
		ReflectionTestUtils.setField(announcement, "id", 10L);
		return announcement;
	}
}
