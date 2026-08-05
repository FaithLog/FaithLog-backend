package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.domain.entity.AnnouncementDocument;
import com.faithlog.announcement.domain.entity.AnnouncementImage;
import com.faithlog.announcement.domain.entity.AnnouncementNotificationOutbox;
import com.faithlog.announcement.infrastructure.repository.AnnouncementCategoryRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementDocumentRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementNotificationOutboxRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementRepository;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementNotificationOutboxRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.infrastructure.repository.MediaAssetRepository;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.notification.domain.entity.NotificationLog;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AnnouncementDeleteIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-08-03T05:00:00Z");

	@Autowired private AnnouncementCommandService service;
	@Autowired private CampusService campusService;
	@Autowired private UserRepository users;
	@Autowired private AnnouncementRepository announcements;
	@Autowired private AnnouncementCategoryRepository categories;
	@Autowired private AnnouncementImageRepository images;
	@Autowired private AnnouncementDocumentRepository documents;
	@MockitoSpyBean private AnnouncementNotificationOutboxRepository outboxes;
	@Autowired private MediaAssetRepository mediaAssets;
	@Autowired private NotificationLogRepository notificationLogs;
	@Autowired private PlatformTransactionManager transactionManager;

	@Test
	void delete_archived_announcement_removes_links_outbox_and_parent_but_keeps_logs_and_other_media() {
		Fixture fixture = transaction().execute(status -> createArchivedFixture("success"));

		service.deleteAnnouncement(fixture.campusId(), fixture.announcementId(), fixture.managerId());

		transaction().executeWithoutResult(status -> {
			assertThat(announcements.findById(fixture.announcementId())).isEmpty();
			assertThat(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(fixture.announcementId())).isEmpty();
			assertThat(documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(fixture.announcementId())).isEmpty();
			assertThat(outboxes.findAll()).noneMatch(outbox -> outbox.announcementId().equals(fixture.announcementId()));
			assertThat(mediaAssets.findById(fixture.imageAssetId())).get()
				.extracting(MediaAsset::status).isEqualTo(MediaAssetStatus.ORPHANED);
			assertThat(mediaAssets.findById(fixture.documentAssetId())).get()
				.extracting(MediaAsset::status).isEqualTo(MediaAssetStatus.ORPHANED);
			assertThat(notificationLogs.findByRequestIdOrderByIdAsc(fixture.notificationRequestId()))
				.singleElement()
				.satisfies(log -> {
					assertThat(log.targetId()).isEqualTo(fixture.announcementId());
					assertThat(log.notificationType()).isEqualTo(NotificationType.ANNOUNCEMENT_PUBLISHED);
				});
			assertThat(announcements.findById(fixture.otherAnnouncementId())).isPresent();
			assertThat(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(fixture.otherAnnouncementId()))
				.extracting(AnnouncementImage::mediaAssetId)
				.containsExactly(fixture.otherImageAssetId());
			assertThat(mediaAssets.findById(fixture.otherImageAssetId())).get()
				.extracting(MediaAsset::status).isEqualTo(MediaAssetStatus.READY);
			assertThat(announcements.findById(fixture.otherCampusAnnouncementId())).isPresent();
			assertThat(mediaAssets.findById(fixture.otherCampusImageAssetId())).get()
				.extracting(MediaAsset::status).isEqualTo(MediaAssetStatus.READY);
		});

		assertThatThrownBy(() -> service.deleteAnnouncement(
			fixture.campusId(), fixture.announcementId(), fixture.managerId()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
	}

	@Test
	void wrong_tenant_or_non_archived_delete_does_not_mutate_attachment_rows() {
		Fixture fixture = transaction().execute(status -> createArchivedFixture("tenant"));

		assertThatThrownBy(() -> service.deleteAnnouncement(
			fixture.otherCampusId(), fixture.announcementId(), fixture.managerId()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

		transaction().executeWithoutResult(status -> {
			assertThat(announcements.findById(fixture.announcementId())).isPresent();
			assertThat(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(fixture.announcementId()))
				.extracting(AnnouncementImage::mediaAssetId)
				.containsExactly(fixture.imageAssetId());
			assertThat(mediaAssets.findById(fixture.imageAssetId())).get()
				.extracting(MediaAsset::status).isEqualTo(MediaAssetStatus.READY);
		});

		NonArchivedFixture nonArchived = transaction().execute(status -> createNonArchivedFixture("published"));
		assertThatThrownBy(() -> service.deleteAnnouncement(
			nonArchived.campusId(), nonArchived.announcementId(), nonArchived.managerId()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT));

		transaction().executeWithoutResult(status -> {
			assertThat(announcements.findById(nonArchived.announcementId())).isPresent();
			assertThat(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(nonArchived.announcementId()))
				.extracting(AnnouncementImage::mediaAssetId)
				.containsExactly(nonArchived.imageAssetId());
			assertThat(outboxes.findAll()).anyMatch(outbox -> outbox.announcementId().equals(nonArchived.announcementId()));
			assertThat(mediaAssets.findById(nonArchived.imageAssetId())).get()
				.extracting(MediaAsset::status).isEqualTo(MediaAssetStatus.READY);
		});
	}

	@Test
	void outbox_delete_failure_rolls_back_orphan_and_link_deletions() {
		Fixture fixture = transaction().execute(status -> createArchivedFixture("rollback"));
		doThrow(new IllegalStateException("outbox failure"))
			.when(outboxes).deleteByAnnouncementId(fixture.announcementId());

		assertThatThrownBy(() -> service.deleteAnnouncement(
			fixture.campusId(), fixture.announcementId(), fixture.managerId()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("outbox failure");

		transaction().executeWithoutResult(status -> {
			assertThat(announcements.findById(fixture.announcementId())).isPresent();
			assertThat(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(fixture.announcementId()))
				.extracting(AnnouncementImage::mediaAssetId)
				.containsExactly(fixture.imageAssetId());
			assertThat(documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(fixture.announcementId()))
				.extracting(AnnouncementDocument::mediaAssetId)
				.containsExactly(fixture.documentAssetId());
			assertThat(outboxes.findAll()).anyMatch(outbox -> outbox.announcementId().equals(fixture.announcementId()));
			assertThat(mediaAssets.findById(fixture.imageAssetId())).get()
				.extracting(MediaAsset::status).isEqualTo(MediaAssetStatus.READY);
			assertThat(mediaAssets.findById(fixture.documentAssetId())).get()
				.extracting(MediaAsset::status).isEqualTo(MediaAssetStatus.READY);
		});
	}

	private Fixture createArchivedFixture(String suffix) {
		User manager = saveManager(suffix);
		Long campusId = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "243-" + suffix, "분당", "공지 삭제 통합 테스트")).campusId();
		Long otherCampusId = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "243-other-" + suffix, "분당", "공지 삭제 타 캠퍼스")).campusId();
		AnnouncementCategory category = saveCategory(campusId, suffix);
		Announcement announcement = saveArchivedAnnouncement(campusId, category.id(), manager.id(), suffix);
		MediaAsset image = saveMedia(readyImage(campusId, manager.id(), suffix + "-image"));
		MediaAsset document = saveMedia(readyPdf(campusId, manager.id(), suffix + "-document"));
		images.save(AnnouncementImage.create(campusId, announcement.id(), image.id(), 0));
		documents.save(AnnouncementDocument.create(campusId, announcement.id(), document.id(), 0));
		saveOutbox(AnnouncementNotificationOutbox.create(
			announcement.id(), campusId, category.id(), manager.id(), category.name(), announcement.title(), NOW));
		UUID requestId = UUID.nameUUIDFromBytes(("announcement-delete-" + suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		notificationLogs.save(NotificationLog.pending(
			requestId,
			manager.id(),
			campusId,
			NotificationType.ANNOUNCEMENT_PUBLISHED,
			null,
			announcement.id(),
			"새 공지가 등록되었어요",
			"[" + category.name() + "] " + announcement.title(),
			Map.of("announcementId", announcement.id().toString())));

		Announcement otherAnnouncement = saveArchivedAnnouncement(campusId, category.id(), manager.id(), suffix + "-other");
		MediaAsset otherImage = saveMedia(readyImage(campusId, manager.id(), suffix + "-other-image"));
		images.save(AnnouncementImage.create(campusId, otherAnnouncement.id(), otherImage.id(), 0));
		AnnouncementCategory otherCampusCategory = saveCategory(otherCampusId, suffix + "-other-campus");
		Announcement otherCampusAnnouncement = saveArchivedAnnouncement(
			otherCampusId, otherCampusCategory.id(), manager.id(), suffix + "-other-campus");
		MediaAsset otherCampusImage = saveMedia(
			readyImage(otherCampusId, manager.id(), suffix + "-other-campus-image"));
		images.save(AnnouncementImage.create(otherCampusId, otherCampusAnnouncement.id(), otherCampusImage.id(), 0));
		announcements.flush();
		images.flush();
		documents.flush();
		outboxes.flush();
		mediaAssets.flush();
		notificationLogs.flush();

		return new Fixture(
			manager.id(),
			campusId,
			otherCampusId,
			announcement.id(),
			image.id(),
			document.id(),
			otherAnnouncement.id(),
			otherImage.id(),
			otherCampusAnnouncement.id(),
			otherCampusImage.id(),
			requestId);
	}

	private NonArchivedFixture createNonArchivedFixture(String suffix) {
		User manager = saveManager(suffix);
		Long campusId = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "243-non-archived-" + suffix, "분당", "공지 삭제 상태 테스트")).campusId();
		AnnouncementCategory category = saveCategory(campusId, suffix);
		Announcement announcement = saveAnnouncement(Announcement.createPublished(
			campusId, category.id(), manager.id(), "공지 " + suffix, "본문", false, NOW));
		MediaAsset image = saveMedia(readyImage(campusId, manager.id(), suffix + "-non-archived-image"));
		images.save(AnnouncementImage.create(campusId, announcement.id(), image.id(), 0));
		saveOutbox(AnnouncementNotificationOutbox.create(
			announcement.id(), campusId, category.id(), manager.id(), category.name(), announcement.title(), NOW));
		announcements.flush();
		images.flush();
		outboxes.flush();
		mediaAssets.flush();
		return new NonArchivedFixture(manager.id(), campusId, announcement.id(), image.id());
	}

	private User saveManager(String suffix) {
		User user = User.create("관리자 " + suffix, "announcement-delete-" + suffix + "@example.com", "encoded");
		user.changeRole(UserRole.MANAGER);
		return users.saveAndFlush(user);
	}

	private AnnouncementCategory saveCategory(Long campusId, String suffix) {
		return ((AnnouncementCategoryRepositoryPort) categories)
			.saveAndFlush(AnnouncementCategory.create(campusId, "삭제 " + suffix, "#3B82F6", 0));
	}

	private Announcement saveArchivedAnnouncement(Long campusId, Long categoryId, Long managerId, String suffix) {
		Announcement announcement = saveAnnouncement(Announcement.createPublished(
			campusId, categoryId, managerId, "공지 " + suffix, "본문", false, NOW));
		announcement.archive();
		announcements.flush();
		return announcement;
	}

	private Announcement saveAnnouncement(Announcement announcement) {
		return ((AnnouncementRepositoryPort) announcements).save(announcement);
	}

	private MediaAsset saveMedia(MediaAsset asset) {
		return ((MediaAssetRepositoryPort) mediaAssets).save(asset);
	}

	private AnnouncementNotificationOutbox saveOutbox(AnnouncementNotificationOutbox outbox) {
		return ((AnnouncementNotificationOutboxRepositoryPort) outboxes).save(outbox);
	}

	private MediaAsset readyImage(Long campusId, Long ownerId, String suffix) {
		MediaAsset asset = MediaAsset.reserve(
			campusId,
			ownerId,
			"image/jpeg",
			10,
			sha256("a", suffix),
			"temporary/announcement-delete/" + suffix + "/original",
			NOW.plusSeconds(3600));
		asset.startProcessing();
		asset.complete(
			"media/announcement-delete/" + suffix + "/thumbnail.jpg",
			"media/announcement-delete/" + suffix + "/detail.jpg",
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
			"temporary/announcement-delete/" + suffix + "/original.pdf",
			NOW.plusSeconds(3600),
			"notice-" + suffix + ".pdf");
		asset.startProcessing();
		asset.completePdf(
			"media/announcement-delete/" + suffix + "/document.pdf",
			10,
			sha256("d", suffix));
		return asset;
	}

	private String sha256(String prefix, String suffix) {
		String hex = Integer.toHexString(Math.abs((prefix + suffix).hashCode()));
		return (prefix + hex).repeat(64).substring(0, 64);
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private record Fixture(
		Long managerId,
		Long campusId,
		Long otherCampusId,
		Long announcementId,
		Long imageAssetId,
		Long documentAssetId,
		Long otherAnnouncementId,
		Long otherImageAssetId,
		Long otherCampusAnnouncementId,
		Long otherCampusImageAssetId,
		UUID notificationRequestId
	) {
	}

	private record NonArchivedFixture(Long managerId, Long campusId, Long announcementId, Long imageAssetId) {
	}
}
