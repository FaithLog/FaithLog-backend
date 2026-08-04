package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.service.command.CreateAnnouncementCommand;
import com.faithlog.announcement.service.command.UpdateAnnouncementCommand;
import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementPublishedEventPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnnouncementCommandServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-03T03:00:00Z");

	@Mock
	private AnnouncementRepositoryPort announcementRepository;
	@Mock
	private AnnouncementCategoryRepositoryPort categoryRepository;
	@Mock
	private AnnouncementAccessPolicy accessPolicy;
	@Mock
	private AnnouncementPublishedEventPort publishedEventPort;

	private AnnouncementCommandService service;

	@BeforeEach
	void setUp() {
		service = new AnnouncementCommandService(
			announcementRepository,
			categoryRepository,
			accessPolicy,
			publishedEventPort,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void create_without_publishAt_publishes_and_records_event_in_same_use_case() {
		AnnouncementCategory category = activeCategory(1L);
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(category));
		when(announcementRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
			Announcement announcement = invocation.getArgument(0);
			ReflectionTestUtils.setField(announcement, "id", 100L);
			return announcement;
		});

		var result = service.createAnnouncement(new CreateAnnouncementCommand(
			1L, 10L, 2L, "공지", "본문", true, null
		));

		verify(accessPolicy).requireManager(1L, 10L);
		assertThat(result.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(result.publishedAt()).isEqualTo(NOW);
		verify(publishedEventPort).recordPublished(org.mockito.ArgumentMatchers.any(Announcement.class),
			org.mockito.ArgumentMatchers.same(category));
	}

	@Test
	void create_with_future_publishAt_is_scheduled_without_event() {
		AnnouncementCategory category = activeCategory(1L);
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(category));
		when(announcementRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.createAnnouncement(new CreateAnnouncementCommand(
			1L, 10L, 2L, "공지", "본문", false, NOW.plusSeconds(3600)
		));

		assertThat(result.status()).isEqualTo(AnnouncementStatus.SCHEDULED);
		verify(publishedEventPort, never()).recordPublished(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void invalid_schedule_time_is_a_typed_validation_failure() {
		AnnouncementCategory category = activeCategory(1L);
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(category));

		assertThatThrownBy(() -> service.createAnnouncement(new CreateAnnouncementCommand(
			1L, 10L, 2L, "공지", "본문", false, NOW.minusSeconds(1)
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.GLOBAL_VALIDATION_FAILED));
	}

	@Test
	void missing_schedule_time_on_scheduled_update_is_a_typed_validation_failure() {
		Announcement announcement = Announcement.createScheduled(
			1L, 2L, 10L, "공지", "본문", false, NOW.plusSeconds(3600), NOW.minusSeconds(10));
		ReflectionTestUtils.setField(announcement, "id", 100L);
		AnnouncementCategory category = activeCategory(1L);
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.of(announcement));
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(category));

		assertThatThrownBy(() -> service.updateAnnouncement(new UpdateAnnouncementCommand(
			1L, 100L, 20L, 2L, "수정", "본문", false, null
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.GLOBAL_VALIDATION_FAILED));
	}

	@Test
	void inactive_category_is_rejected_for_create_and_update() {
		AnnouncementCategory inactive = activeCategory(1L);
		inactive.deactivate();
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(inactive));

		assertThatThrownBy(() -> service.createAnnouncement(new CreateAnnouncementCommand(
			1L, 10L, 2L, "공지", "본문", false, null
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_CATEGORY_INACTIVE));
	}

	@Test
	void existing_inactive_category_does_not_block_content_update_or_manual_publish() {
		Announcement announcement = Announcement.createScheduled(
			1L, 2L, 10L, "공지", "본문", false, NOW.plusSeconds(3600), NOW.minusSeconds(10));
		ReflectionTestUtils.setField(announcement, "id", 100L);
		AnnouncementCategory inactive = activeCategory(1L);
		inactive.deactivate();
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.of(announcement));
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(inactive));

		var updated = service.updateAnnouncement(new UpdateAnnouncementCommand(
			1L, 100L, 20L, 2L, "수정", "수정 본문", true, NOW.plusSeconds(7200)));
		var published = service.publishAnnouncement(1L, 100L, 20L);

		assertThat(updated.title()).isEqualTo("수정");
		assertThat(published.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		verify(publishedEventPort).recordPublished(announcement, inactive);
	}

	@Test
	void changing_an_existing_announcement_to_an_inactive_category_is_rejected() {
		Announcement announcement = Announcement.createPublished(1L, 2L, 10L, "공지", "본문", false, NOW);
		ReflectionTestUtils.setField(announcement, "id", 100L);
		AnnouncementCategory inactive = AnnouncementCategory.create(1L, "행사", "#ABCDEF", 1);
		ReflectionTestUtils.setField(inactive, "id", 3L);
		inactive.deactivate();
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.of(announcement));
		when(categoryRepository.findByCampusIdAndId(1L, 3L)).thenReturn(Optional.of(inactive));

		assertThatThrownBy(() -> service.updateAnnouncement(new UpdateAnnouncementCommand(
			1L, 100L, 20L, 3L, "수정", "본문", false, null)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_CATEGORY_INACTIVE));
	}

	@Test
	void manual_publish_locks_row_and_records_only_one_published_event() {
		Announcement announcement = Announcement.createScheduled(
			1L, 2L, 10L, "공지", "본문", false, NOW.plusSeconds(3600), NOW.minusSeconds(10)
		);
		ReflectionTestUtils.setField(announcement, "id", 100L);
		AnnouncementCategory category = activeCategory(1L);
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.of(announcement));
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(category));

		var result = service.publishAnnouncement(1L, 100L, 20L);

		assertThat(result.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		verify(publishedEventPort).recordPublished(announcement, category);
	}

	@Test
	void published_update_does_not_record_event_and_archive_has_no_restore_path() {
		Announcement announcement = Announcement.createPublished(1L, 2L, 10L, "공지", "본문", false, NOW);
		ReflectionTestUtils.setField(announcement, "id", 100L);
		AnnouncementCategory category = activeCategory(1L);
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.of(announcement));
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(category));

		service.updateAnnouncement(new UpdateAnnouncementCommand(
			1L, 100L, 20L, 2L, "수정", "수정 본문", true, null
		));
		service.archiveAnnouncement(1L, 100L, 20L);

		assertThat(announcement.status()).isEqualTo(AnnouncementStatus.ARCHIVED);
		verify(publishedEventPort, never()).recordPublished(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertThatThrownBy(() -> service.publishAnnouncement(1L, 100L, 20L))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT));
	}

	private AnnouncementCategory activeCategory(Long campusId) {
		AnnouncementCategory category = AnnouncementCategory.create(campusId, "일반", "#ABCDEF", 0);
		ReflectionTestUtils.setField(category, "id", 2L);
		return category;
	}
}
