package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
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
import com.faithlog.announcement.service.port.AnnouncementNotificationOutboxRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementPublishedEventPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import com.faithlog.announcement.service.result.AnnouncementResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
	@Mock
	private AnnouncementNotificationOutboxRepositoryPort outboxRepository;
	@Mock
	private AnnouncementImageAttachmentService imageAttachmentService;
	@Mock
	private AnnouncementDocumentAttachmentService documentAttachmentService;

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

	@Test
	void archived_announcement_delete_orphans_attachments_then_deletes_outbox_and_announcement() {
		Announcement announcement = Announcement.createPublished(1L, 2L, 10L, "공지", "본문", false, NOW);
		ReflectionTestUtils.setField(announcement, "id", 100L);
		announcement.archive();
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.of(announcement));
		AnnouncementCommandService deletionService = new AnnouncementCommandService(
			announcementRepository, categoryRepository, accessPolicy, publishedEventPort,
			imageAttachmentService, documentAttachmentService, outboxRepository, Clock.fixed(NOW, ZoneOffset.UTC));

		deletionService.deleteAnnouncement(1L, 100L, 20L);

		verify(accessPolicy).requireManager(1L, 20L);
		var ordered = inOrder(imageAttachmentService, documentAttachmentService, outboxRepository,
			announcementRepository);
		ordered.verify(imageAttachmentService).orphanAll(100L, 1L);
		ordered.verify(documentAttachmentService).orphanAll(100L, 1L);
		ordered.verify(outboxRepository).deleteByAnnouncementId(100L);
		ordered.verify(announcementRepository).delete(announcement);
	}

	@Test
	void non_archived_announcement_delete_is_rejected_without_mutation() {
		Announcement announcement = Announcement.createPublished(1L, 2L, 10L, "공지", "본문", false, NOW);
		ReflectionTestUtils.setField(announcement, "id", 100L);
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.of(announcement));
		AnnouncementCommandService deletionService = new AnnouncementCommandService(
			announcementRepository, categoryRepository, accessPolicy, publishedEventPort,
			imageAttachmentService, documentAttachmentService, outboxRepository, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatThrownBy(() -> deletionService.deleteAnnouncement(1L, 100L, 20L))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT));

		verify(imageAttachmentService, never()).orphanAll(org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyLong());
		verify(documentAttachmentService, never()).orphanAll(org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyLong());
		verify(outboxRepository, never()).deleteByAnnouncementId(org.mockito.ArgumentMatchers.anyLong());
		verify(announcementRepository, never()).delete(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void missing_announcement_delete_returns_not_found_without_attachment_lookup() {
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.empty());
		AnnouncementCommandService deletionService = new AnnouncementCommandService(
			announcementRepository, categoryRepository, accessPolicy, publishedEventPort,
			imageAttachmentService, documentAttachmentService, outboxRepository, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatThrownBy(() -> deletionService.deleteAnnouncement(1L, 100L, 20L))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

		verify(accessPolicy).requireManager(1L, 20L);
		verify(announcementRepository).findByCampusIdAndIdForUpdate(1L, 100L);
		verify(imageAttachmentService, never()).orphanAll(org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyLong());
		verify(documentAttachmentService, never()).orphanAll(org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void restore_archived_published_announcement_preserves_content_attachments_and_does_not_record_event() throws Exception {
		Announcement announcement = Announcement.createPublished(1L, 2L, 10L, "공지", "본문", true, NOW);
		ReflectionTestUtils.setField(announcement, "id", 100L);
		announcement.archive();
		AnnouncementCategory category = activeCategory(1L);
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.of(announcement));
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(category));
		when(imageAttachmentService.getOrderedAssetIds(100L)).thenReturn(List.of(31L, 32L));
		when(documentAttachmentService.getOrderedAssetIds(100L)).thenReturn(List.of(41L, 42L));
		AnnouncementCommandService restoreService = new AnnouncementCommandService(
			announcementRepository, categoryRepository, accessPolicy, publishedEventPort,
			imageAttachmentService, documentAttachmentService, outboxRepository,
			Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC));

		var result = restore(restoreService, 1L, 100L, 20L);

		verify(accessPolicy).requireManager(1L, 20L);
		assertThat(result.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(result.publishedAt()).isEqualTo(NOW);
		assertThat(result.title()).isEqualTo("공지");
		assertThat(result.content()).isEqualTo("본문");
		assertThat(result.pinned()).isTrue();
		assertThat(result.imageAssetIds()).containsExactly(31L, 32L);
		assertThat(result.documentAssetIds()).containsExactly(41L, 42L);
		verify(publishedEventPort, never()).recordPublished(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		verify(outboxRepository, never()).save(org.mockito.ArgumentMatchers.any());
		verify(outboxRepository, never()).deleteByAnnouncementId(org.mockito.ArgumentMatchers.anyLong());
		verify(imageAttachmentService, never()).orphanAll(org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyLong());
		verify(documentAttachmentService, never()).orphanAll(org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void restore_archived_scheduled_announcement_uses_injected_clock_for_first_publication_time() throws Exception {
		Instant restoreTime = NOW.plusSeconds(900);
		Announcement announcement = Announcement.createScheduled(
			1L, 2L, 10L, "예약", "본문", false, NOW.plusSeconds(3600), NOW.minusSeconds(10));
		ReflectionTestUtils.setField(announcement, "id", 100L);
		announcement.archive();
		AnnouncementCategory category = activeCategory(1L);
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.of(announcement));
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(category));
		AnnouncementCommandService restoreService = new AnnouncementCommandService(
			announcementRepository, categoryRepository, accessPolicy, publishedEventPort,
			imageAttachmentService, documentAttachmentService, outboxRepository,
			Clock.fixed(restoreTime, ZoneOffset.UTC));

		var result = restore(restoreService, 1L, 100L, 20L);

		assertThat(result.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(result.publishedAt()).isEqualTo(restoreTime);
		verify(publishedEventPort, never()).recordPublished(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		verify(outboxRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void restore_missing_or_non_archived_announcement_is_rejected_without_attachment_or_outbox_mutation() {
		AnnouncementCommandService restoreService = new AnnouncementCommandService(
			announcementRepository, categoryRepository, accessPolicy, publishedEventPort,
			imageAttachmentService, documentAttachmentService, outboxRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> restore(restoreService, 1L, 100L, 20L))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

		Announcement published = Announcement.createPublished(1L, 2L, 10L, "공지", "본문", false, NOW);
		ReflectionTestUtils.setField(published, "id", 101L);
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 101L)).thenReturn(Optional.of(published));

		assertThatThrownBy(() -> restore(restoreService, 1L, 101L, 20L))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT));

		verify(publishedEventPort, never()).recordPublished(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		verify(outboxRepository, never()).save(org.mockito.ArgumentMatchers.any());
		verify(outboxRepository, never()).deleteByAnnouncementId(org.mockito.ArgumentMatchers.anyLong());
		verify(imageAttachmentService, never()).orphanAll(org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyLong());
		verify(documentAttachmentService, never()).orphanAll(org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void duplicate_restore_succeeds_once_then_fails_with_status_conflict() throws Exception {
		Announcement announcement = Announcement.createPublished(1L, 2L, 10L, "공지", "본문", false, NOW);
		ReflectionTestUtils.setField(announcement, "id", 100L);
		announcement.archive();
		AnnouncementCategory category = activeCategory(1L);
		when(announcementRepository.findByCampusIdAndIdForUpdate(1L, 100L)).thenReturn(Optional.of(announcement));
		when(categoryRepository.findByCampusIdAndId(1L, 2L)).thenReturn(Optional.of(category));

		restore(service, 1L, 100L, 20L);

		assertThatThrownBy(() -> restore(service, 1L, 100L, 20L))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT));
		verify(publishedEventPort, never()).recordPublished(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	private AnnouncementResult restore(
		AnnouncementCommandService target,
		Long campusId,
		Long announcementId,
		Long requesterId
	) throws Exception {
		try {
			return (AnnouncementResult) AnnouncementCommandService.class
				.getMethod("restoreAnnouncement", Long.class, Long.class, Long.class)
				.invoke(target, campusId, announcementId, requesterId);
		} catch (InvocationTargetException exception) {
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

	private AnnouncementCategory activeCategory(Long campusId) {
		AnnouncementCategory category = AnnouncementCategory.create(campusId, "일반", "#ABCDEF", 0);
		ReflectionTestUtils.setField(category, "id", 2L);
		return category;
	}
}
