package com.faithlog.announcement.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.announcement.domain.type.AnnouncementStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnnouncementTest {

	private static final Instant NOW = Instant.parse("2026-08-03T03:00:00Z");

	@Test
	void createPublished_trims_text_and_sets_published_state() {
		Announcement announcement = Announcement.createPublished(
			1L, 2L, 3L, "  예배 안내  ", "  본문입니다.  ", true, NOW
		);

		assertThat(announcement.campusId()).isEqualTo(1L);
		assertThat(announcement.categoryId()).isEqualTo(2L);
		assertThat(announcement.authorId()).isEqualTo(3L);
		assertThat(announcement.title()).isEqualTo("예배 안내");
		assertThat(announcement.content()).isEqualTo("본문입니다.");
		assertThat(announcement.isPinned()).isTrue();
		assertThat(announcement.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(announcement.publishAt()).isEqualTo(NOW);
		assertThat(announcement.publishedAt()).isEqualTo(NOW);
	}

	@Test
	void createScheduled_requires_future_publish_time() {
		Instant future = NOW.plusSeconds(3600);

		Announcement announcement = Announcement.createScheduled(
			1L, 2L, 3L, "공지", "본문", false, future, NOW
		);

		assertThat(announcement.status()).isEqualTo(AnnouncementStatus.SCHEDULED);
		assertThat(announcement.publishAt()).isEqualTo(future);
		assertThat(announcement.publishedAt()).isNull();
		assertThatThrownBy(() -> Announcement.createScheduled(
			1L, 2L, 3L, "공지", "본문", false, NOW, NOW
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void create_accepts_exact_text_boundaries_and_rejects_blank_or_oversized_text() {
		Announcement boundary = Announcement.createPublished(
			1L, 2L, 3L, "가".repeat(100), "나".repeat(5000), false, NOW
		);

		assertThat(boundary.title()).hasSize(100);
		assertThat(boundary.content()).hasSize(5000);
		assertThatThrownBy(() -> Announcement.createPublished(
			1L, 2L, 3L, " ", "본문", false, NOW
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Announcement.createPublished(
			1L, 2L, 3L, "공지", " ", false, NOW
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Announcement.createPublished(
			1L, 2L, 3L, "가".repeat(101), "본문", false, NOW
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Announcement.createPublished(
			1L, 2L, 3L, "공지", "나".repeat(5001), false, NOW
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void scheduled_announcement_can_be_rescheduled_published_and_archived() {
		Announcement announcement = Announcement.createScheduled(
			1L, 2L, 3L, "공지", "본문", false, NOW.plusSeconds(3600), NOW
		);

		announcement.update(4L, "수정 공지", "수정 본문", true, NOW.plusSeconds(7200), NOW);
		announcement.publish(NOW.plusSeconds(100));
		announcement.archive();

		assertThat(announcement.categoryId()).isEqualTo(4L);
		assertThat(announcement.title()).isEqualTo("수정 공지");
		assertThat(announcement.content()).isEqualTo("수정 본문");
		assertThat(announcement.isPinned()).isTrue();
		assertThat(announcement.publishAt()).isEqualTo(NOW.plusSeconds(100));
		assertThat(announcement.publishedAt()).isEqualTo(NOW.plusSeconds(100));
		assertThat(announcement.status()).isEqualTo(AnnouncementStatus.ARCHIVED);
	}

	@Test
	void published_update_does_not_change_publication_time_and_archived_cannot_be_updated_or_published_directly() {
		Announcement announcement = Announcement.createPublished(
			1L, 2L, 3L, "공지", "본문", false, NOW
		);

		announcement.update(4L, "수정", "수정 본문", true, null, NOW.plusSeconds(10));

		assertThat(announcement.publishAt()).isEqualTo(NOW);
		assertThat(announcement.publishedAt()).isEqualTo(NOW);
		assertThatThrownBy(() -> announcement.update(
			4L, "수정", "수정 본문", true, NOW.plusSeconds(100), NOW.plusSeconds(10)
		)).isInstanceOf(IllegalStateException.class);

		announcement.archive();
		assertThatThrownBy(() -> announcement.update(
			4L, "다시 수정", "본문", false, null, NOW.plusSeconds(20)
		)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> announcement.publish(NOW.plusSeconds(20)))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void archived_published_announcement_restore_preserves_original_publication_time() throws Exception {
		Announcement announcement = Announcement.createPublished(
			1L, 2L, 3L, "공지", "본문", false, NOW
		);
		announcement.archive();

		restore(announcement, NOW.plusSeconds(3600));

		assertThat(announcement.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(announcement.publishedAt()).isEqualTo(NOW);
		assertThat(announcement.publishAt()).isEqualTo(NOW);
	}

	@Test
	void archived_scheduled_announcement_restore_uses_restore_time_when_never_published() throws Exception {
		Announcement announcement = Announcement.createScheduled(
			1L, 2L, 3L, "공지", "본문", false, NOW.plusSeconds(3600), NOW
		);
		announcement.archive();
		Instant restoreTime = NOW.plusSeconds(120);

		restore(announcement, restoreTime);

		assertThat(announcement.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(announcement.publishedAt()).isEqualTo(restoreTime);
		assertThat(announcement.publishAt()).isEqualTo(restoreTime);
	}

	@Test
	void non_archived_announcement_restore_is_rejected() {
		Announcement announcement = Announcement.createPublished(
			1L, 2L, 3L, "공지", "본문", false, NOW
		);

		assertThatThrownBy(() -> restore(announcement, NOW.plusSeconds(1)))
			.isInstanceOf(IllegalStateException.class);
	}

	private void restore(Announcement announcement, Instant now) throws Exception {
		try {
			Announcement.class.getMethod("restore", Instant.class).invoke(announcement, now);
		} catch (java.lang.reflect.InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new AssertionError(cause);
		}
	}
}
