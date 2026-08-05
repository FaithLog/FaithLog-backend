package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.domain.entity.AnnouncementDocument;
import com.faithlog.announcement.domain.entity.AnnouncementImage;
import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.infrastructure.repository.AnnouncementCategoryRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementDocumentRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementNotificationOutboxRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementRepository;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import com.faithlog.announcement.service.result.AnnouncementResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.infrastructure.repository.MediaAssetRepository;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AnnouncementRestoreIntegrationTest {

	private static final Instant ORIGINAL_PUBLISHED_AT = Instant.parse("2026-08-03T05:00:00Z");
	private static final Instant RESTORE_NOW = Instant.parse("2026-08-05T01:23:45Z");

	@Autowired private AnnouncementCommandService service;
	@Autowired private AnnouncementQueryService queryService;
	@Autowired private CampusService campusService;
	@Autowired private UserRepository users;
	@Autowired private AnnouncementRepository announcements;
	@Autowired private AnnouncementCategoryRepository categories;
	@Autowired private AnnouncementImageRepository images;
	@Autowired private AnnouncementDocumentRepository documents;
	@Autowired private AnnouncementNotificationOutboxRepository outboxes;
	@Autowired private MediaAssetRepository mediaAssets;
	@Autowired private NotificationLogRepository notificationLogs;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private AnnouncementRepositoryLockProbe lockProbe;

	@Test
	void restore_published_then_archived_preserves_publication_time_and_published_visibility() throws Exception {
		RestoreFixture fixture = transaction().execute(status -> createArchivedFixture("published", true));

		AnnouncementResult result = restore(fixture.campusId(), fixture.announcementId(), fixture.managerId());

		assertThat(result.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(result.publishedAt()).isEqualTo(ORIGINAL_PUBLISHED_AT);
		assertThat(result.title()).isEqualTo("복구 공지 published");
		assertThat(result.content()).isEqualTo("복구 본문 published");
		assertThat(result.authorId()).isEqualTo(fixture.managerId());
		assertThat(result.imageAssetIds()).containsExactlyElementsOf(fixture.imageAssetIds());
		assertThat(result.documentAssetIds()).containsExactlyElementsOf(fixture.documentAssetIds());

		var list = queryService.getAnnouncements(
			fixture.campusId(), fixture.managerId(), AnnouncementStatus.PUBLISHED, PageRequest.of(0, 20));
		assertThat(list.getContent()).extracting(AnnouncementResult::id)
			.contains(fixture.announcementId());
		assertThat(queryService.getAnnouncement(fixture.campusId(), fixture.announcementId(), fixture.managerId())
			.status()).isEqualTo(AnnouncementStatus.PUBLISHED);

		assertNoRestoreSideEffects(fixture);
	}

	@Test
	void restore_scheduled_then_archived_uses_injected_clock_for_first_publication_time() throws Exception {
		RestoreFixture fixture = transaction().execute(status -> createArchivedFixture("scheduled", false));

		AnnouncementResult result = restore(fixture.campusId(), fixture.announcementId(), fixture.managerId());

		assertThat(result.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(result.publishedAt()).isEqualTo(RESTORE_NOW);
		assertThat(result.imageAssetIds()).containsExactlyElementsOf(fixture.imageAssetIds());
		assertThat(result.documentAssetIds()).containsExactlyElementsOf(fixture.documentAssetIds());
		assertNoRestoreSideEffects(fixture);
	}

	@Test
	void restore_not_found_for_missing_deleted_or_other_campus_and_conflict_for_non_archived_or_duplicate() throws Exception {
		RestoreFixture fixture = transaction().execute(status -> createArchivedFixture("boundaries", true));

		assertBusinessError(() -> restore(fixture.otherCampusId(), fixture.announcementId(), fixture.managerId()),
			ErrorCode.ANNOUNCEMENT_NOT_FOUND);
		assertBusinessError(() -> restore(fixture.campusId(), fixture.announcementId() + 999_999L, fixture.managerId()),
			ErrorCode.ANNOUNCEMENT_NOT_FOUND);

		restore(fixture.campusId(), fixture.announcementId(), fixture.managerId());
		assertBusinessError(() -> restore(fixture.campusId(), fixture.announcementId(), fixture.managerId()),
			ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT);

		NonArchivedFixture published = transaction().execute(status -> createNonArchivedFixture("published-boundary", true));
		assertBusinessError(() -> restore(published.campusId(), published.announcementId(), published.managerId()),
			ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT);
		NonArchivedFixture scheduled = transaction().execute(status -> createNonArchivedFixture("scheduled-boundary", false));
		assertBusinessError(() -> restore(scheduled.campusId(), scheduled.announcementId(), scheduled.managerId()),
			ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT);

		RestoreFixture deleted = transaction().execute(status -> createArchivedFixture("deleted-boundary", true));
		service.deleteAnnouncement(deleted.campusId(), deleted.announcementId(), deleted.managerId());
		assertBusinessError(() -> restore(deleted.campusId(), deleted.announcementId(), deleted.managerId()),
			ErrorCode.ANNOUNCEMENT_NOT_FOUND);
	}

	@Test
	void restore_requires_campus_manager_permission_before_mutating_or_locking_announcement() {
		RestoreFixture fixture = transaction().execute(status -> createArchivedFixture("forbidden", true));
		Long outsiderId = users.saveAndFlush(User.create(
			"외부 사용자", "announcement-restore-outsider-" + UUID.randomUUID() + "@example.com", "encoded")).id();

		assertBusinessError(() -> restore(fixture.campusId(), fixture.announcementId(), outsiderId),
			ErrorCode.ANNOUNCEMENT_MANAGE_FORBIDDEN);

		Announcement unchanged = announcements.findById(fixture.announcementId()).orElseThrow();
		assertThat(unchanged.status()).isEqualTo(AnnouncementStatus.ARCHIVED);
		assertThat(unchanged.publishedAt()).isEqualTo(ORIGINAL_PUBLISHED_AT);
	}

	@Test
	void restore_failure_after_state_change_rolls_back_complete_transaction() {
		Announcement announcement = transaction().execute(status -> {
			User manager = saveManager("rollback");
			Long campusId = campusService.createCampus(new CreateCampusCommand(
				manager.id(), "244-rollback-" + UUID.randomUUID(), "분당", "복구 롤백 테스트")).campusId();
			Announcement target = Announcement.createScheduled(
				campusId, 999_999L, manager.id(), "롤백 공지", "본문", false,
				RESTORE_NOW.plusSeconds(3600), RESTORE_NOW.minusSeconds(60));
			announcements.saveAndFlush(target);
			target.archive();
			announcements.flush();
			return target;
		});

		assertBusinessError(() -> restore(announcement.campusId(), announcement.id(), announcement.authorId()),
			ErrorCode.ANNOUNCEMENT_CATEGORY_NOT_FOUND);

		Announcement rolledBack = announcements.findById(announcement.id()).orElseThrow();
		assertThat(rolledBack.status()).isEqualTo(AnnouncementStatus.ARCHIVED);
		assertThat(rolledBack.publishedAt()).isNull();
	}

	@Test
	@Timeout(10)
	void restore_delete_race_serializes_on_announcement_row_lock_without_partial_orphans() throws Exception {
		RestoreFixture fixture = transaction().execute(status -> createArchivedFixture("race", true));
		CountDownLatch restoreLocked = new CountDownLatch(1);
		CountDownLatch allowRestoreCommit = new CountDownLatch(1);
		CountDownLatch deleteLockAttempted = new CountDownLatch(1);
		lockProbe.blockNextCampusLock(fixture.announcementId(), restoreLocked, allowRestoreCommit);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<AnnouncementResult> restoreFuture = executor.submit(() ->
				restore(fixture.campusId(), fixture.announcementId(), fixture.managerId()));
			await(restoreLocked);
			lockProbe.signalNextCampusLockAttempt(fixture.announcementId(), deleteLockAttempted);
			Future<Throwable> deleteFuture = executor.submit(() -> captureThrowable(() ->
				service.deleteAnnouncement(fixture.campusId(), fixture.announcementId(), fixture.managerId())));
			await(deleteLockAttempted);
			assertThat(deleteFuture.isDone()).isFalse();

			allowRestoreCommit.countDown();

			assertThat(restoreFuture.get(5, TimeUnit.SECONDS).status()).isEqualTo(AnnouncementStatus.PUBLISHED);
			Throwable deleteFailure = deleteFuture.get(5, TimeUnit.SECONDS);
			assertThat(deleteFailure).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT));
		} finally {
			allowRestoreCommit.countDown();
			executor.shutdownNow();
		}

		Announcement restored = announcements.findById(fixture.announcementId()).orElseThrow();
		assertThat(restored.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertNoRestoreSideEffects(fixture);
	}

	private RestoreFixture createArchivedFixture(String suffix, boolean previouslyPublished) {
		User manager = saveManager(suffix);
		Long campusId = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "244-" + suffix + "-" + UUID.randomUUID(), "분당", "공지 복구 테스트")).campusId();
		Long otherCampusId = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "244-other-" + suffix + "-" + UUID.randomUUID(), "분당", "공지 복구 타 캠퍼스")).campusId();
		AnnouncementCategory category = ((AnnouncementCategoryRepositoryPort) categories).saveAndFlush(
			AnnouncementCategory.create(campusId, "복구 " + suffix, "#3B82F6", 0));
		Announcement announcement = previouslyPublished
			? Announcement.createPublished(
				campusId, category.id(), manager.id(), "복구 공지 " + suffix, "복구 본문 " + suffix, true,
				ORIGINAL_PUBLISHED_AT)
			: Announcement.createScheduled(
				campusId, category.id(), manager.id(), "복구 공지 " + suffix, "복구 본문 " + suffix, false,
				RESTORE_NOW.plusSeconds(3600), RESTORE_NOW.minusSeconds(60));
		announcements.saveAndFlush(announcement);
		announcement.archive();
		announcements.flush();

		MediaAsset firstImage = mediaAssets.saveAndFlush(readyImage(campusId, manager.id(), suffix + "-image-1"));
		MediaAsset secondImage = mediaAssets.saveAndFlush(readyImage(campusId, manager.id(), suffix + "-image-2"));
		MediaAsset firstDocument = mediaAssets.saveAndFlush(readyPdf(campusId, manager.id(), suffix + "-document-1"));
		MediaAsset secondDocument = mediaAssets.saveAndFlush(readyPdf(campusId, manager.id(), suffix + "-document-2"));
		images.saveAndFlush(AnnouncementImage.create(campusId, announcement.id(), secondImage.id(), 0));
		images.saveAndFlush(AnnouncementImage.create(campusId, announcement.id(), firstImage.id(), 1));
		documents.saveAndFlush(AnnouncementDocument.create(campusId, announcement.id(), secondDocument.id(), 0));
		documents.saveAndFlush(AnnouncementDocument.create(campusId, announcement.id(), firstDocument.id(), 1));

		return new RestoreFixture(
			manager.id(), campusId, otherCampusId, announcement.id(), List.of(secondImage.id(), firstImage.id()),
			List.of(secondDocument.id(), firstDocument.id()));
	}

	private NonArchivedFixture createNonArchivedFixture(String suffix, boolean published) {
		User manager = saveManager(suffix);
		Long campusId = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "244-non-archived-" + suffix + "-" + UUID.randomUUID(), "분당", "공지 복구 상태 테스트"))
			.campusId();
		AnnouncementCategory category = ((AnnouncementCategoryRepositoryPort) categories).saveAndFlush(
			AnnouncementCategory.create(campusId, "복구 상태 " + suffix, "#3B82F6", 0));
		Announcement announcement = published
			? Announcement.createPublished(campusId, category.id(), manager.id(), "게시 공지", "본문", false,
				ORIGINAL_PUBLISHED_AT)
			: Announcement.createScheduled(campusId, category.id(), manager.id(), "예약 공지", "본문", false,
				RESTORE_NOW.plusSeconds(3600), RESTORE_NOW.minusSeconds(60));
		announcements.saveAndFlush(announcement);
		return new NonArchivedFixture(manager.id(), campusId, announcement.id());
	}

	private User saveManager(String suffix) {
		User user = User.create(
			"관리자 " + suffix,
			"announcement-restore-" + suffix + "-" + UUID.randomUUID() + "@example.com",
			"encoded");
		user.changeRole(UserRole.MANAGER);
		return users.saveAndFlush(user);
	}

	private MediaAsset readyImage(Long campusId, Long ownerId, String suffix) {
		MediaAsset asset = MediaAsset.reserve(
			campusId,
			ownerId,
			"image/jpeg",
			10,
			sha256("a", suffix),
			"temporary/announcement-restore/" + suffix + "/original",
			RESTORE_NOW.plusSeconds(3600));
		asset.startProcessing();
		asset.complete(
			"media/announcement-restore/" + suffix + "/thumbnail.jpg",
			"media/announcement-restore/" + suffix + "/detail.jpg",
			100,
			100,
			20,
			sha256("b", suffix));
		return asset;
	}

	private MediaAsset readyPdf(Long campusId, Long ownerId, String suffix) {
		MediaAsset asset = MediaAsset.reserve(
			campusId,
			ownerId,
			"application/pdf",
			10,
			sha256("c", suffix),
			"temporary/announcement-restore/" + suffix + "/original.pdf",
			RESTORE_NOW.plusSeconds(3600),
			"notice-" + suffix + ".pdf");
		asset.startProcessing();
		asset.completePdf(
			"media/announcement-restore/" + suffix + "/document.pdf",
			10,
			sha256("d", suffix));
		return asset;
	}

	private void assertNoRestoreSideEffects(RestoreFixture fixture) {
		assertThat(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(fixture.announcementId()))
			.extracting(AnnouncementImage::mediaAssetId)
			.containsExactlyElementsOf(fixture.imageAssetIds());
		assertThat(documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(fixture.announcementId()))
			.extracting(AnnouncementDocument::mediaAssetId)
			.containsExactlyElementsOf(fixture.documentAssetIds());
		assertThat(outboxes.findAll()).noneMatch(outbox -> outbox.announcementId().equals(fixture.announcementId()));
		assertThat(notificationLogs.findAll()).noneMatch(log -> fixture.announcementId().equals(log.targetId()));
		for (Long assetId : fixture.imageAssetIds()) {
			assertReadyMedia(fixture.campusId(), fixture.managerId(), assetId);
		}
		for (Long assetId : fixture.documentAssetIds()) {
			assertReadyMedia(fixture.campusId(), fixture.managerId(), assetId);
		}
	}

	private void assertReadyMedia(Long campusId, Long ownerId, Long assetId) {
		MediaAsset asset = mediaAssets.findById(assetId).orElseThrow();
		assertThat(asset.status()).isEqualTo(MediaAssetStatus.READY);
		assertThat(asset.campusId()).isEqualTo(campusId);
		assertThat(asset.ownerUserId()).isEqualTo(ownerId);
	}

	private AnnouncementResult restore(Long campusId, Long announcementId, Long requesterId) throws Exception {
		try {
			return (AnnouncementResult) AnnouncementCommandService.class
				.getMethod("restoreAnnouncement", Long.class, Long.class, Long.class)
				.invoke(service, campusId, announcementId, requesterId);
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

	private void assertBusinessError(ThrowingOperation operation, ErrorCode errorCode) {
		assertThatThrownBy(operation::execute)
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(errorCode));
	}

	private Throwable captureThrowable(ThrowingOperation operation) {
		try {
			operation.execute();
			return null;
		} catch (Throwable throwable) {
			return throwable;
		}
	}

	private String sha256(String prefix, String suffix) {
		String hex = Integer.toHexString(Math.abs((prefix + suffix).hashCode()));
		return (prefix + hex).repeat(64).substring(0, 64);
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RestoreTestConfig {
		@Bean
		@Primary
		Clock fixedApplicationClock() {
			return Clock.fixed(RESTORE_NOW, ZoneOffset.UTC);
		}

		@Bean
		AnnouncementRepositoryLockProbe announcementRepositoryLockProbe() {
			return new AnnouncementRepositoryLockProbe();
		}

		@Bean
		@Primary
		AnnouncementRepositoryPort probingAnnouncementRepositoryPort(
			AnnouncementRepository delegate,
			AnnouncementRepositoryLockProbe probe
		) {
			return (AnnouncementRepositoryPort) Proxy.newProxyInstance(
				AnnouncementRepositoryPort.class.getClassLoader(),
				new Class<?>[] {AnnouncementRepositoryPort.class},
				(proxy, method, args) -> {
					if (method.getName().equals("findByCampusIdAndIdForUpdate")) {
						Long announcementId = (Long) args[1];
						probe.beforeCampusLock(announcementId);
						try {
							Object result = method.invoke(delegate, args);
							probe.afterCampusLock(announcementId);
							return result;
						} catch (InvocationTargetException exception) {
							throw exception.getCause();
						}
					}
					try {
						return method.invoke(delegate, args);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				}
			);
		}
	}

	static class AnnouncementRepositoryLockProbe {
		private final AtomicReference<LockBlock> nextBlock = new AtomicReference<>();
		private final AtomicReference<LockAttemptSignal> nextAttemptSignal = new AtomicReference<>();

		void blockNextCampusLock(Long announcementId, CountDownLatch locked, CountDownLatch release) {
			nextBlock.set(new LockBlock(announcementId, locked, release));
		}

		void signalNextCampusLockAttempt(Long announcementId, CountDownLatch entered) {
			nextAttemptSignal.set(new LockAttemptSignal(announcementId, entered));
		}

		void beforeCampusLock(Long announcementId) {
			LockAttemptSignal signal = nextAttemptSignal.get();
			if (signal != null && signal.announcementId().equals(announcementId)
				&& nextAttemptSignal.compareAndSet(signal, null)) {
				signal.entered().countDown();
			}
		}

		void afterCampusLock(Long announcementId) {
			LockBlock block = nextBlock.get();
			if (block == null || !block.announcementId().equals(announcementId)
				|| !nextBlock.compareAndSet(block, null)) {
				return;
			}
			block.locked().countDown();
			await(block.release());
		}
	}

	private interface ThrowingOperation {
		void execute() throws Throwable;
	}

	private record RestoreFixture(
		Long managerId,
		Long campusId,
		Long otherCampusId,
		Long announcementId,
		List<Long> imageAssetIds,
		List<Long> documentAssetIds
	) {
	}

	private record NonArchivedFixture(Long managerId, Long campusId, Long announcementId) {
	}

	private record LockBlock(Long announcementId, CountDownLatch locked, CountDownLatch release) {
	}

	private record LockAttemptSignal(Long announcementId, CountDownLatch entered) {
	}
}
