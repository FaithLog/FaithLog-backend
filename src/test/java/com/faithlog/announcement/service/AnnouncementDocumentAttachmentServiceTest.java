package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.domain.entity.AnnouncementDocument;
import com.faithlog.announcement.infrastructure.repository.AnnouncementDocumentRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.announcement.service.port.PollMediaAttachmentPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnnouncementDocumentAttachmentServiceTest {

	@Mock private AnnouncementDocumentRepository documents;
	@Mock private AnnouncementImageRepository images;
	@Mock private MediaAssetRepositoryPort assets;
	@Mock private PollMediaAttachmentPort pollAttachments;
	private AnnouncementDocumentAttachmentService service;

	@BeforeEach
	void setUp() {
		service = new AnnouncementDocumentAttachmentService(documents, images, assets, pollAttachments);
	}

	@Test
	void co_manager_can_reorder_existing_pdf_but_cannot_add_unattached_other_owner_pdf() {
		MediaAsset first = readyPdf(31L, 7L, 11L);
		MediaAsset second = readyPdf(32L, 7L, 11L);
		when(documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of(
			AnnouncementDocument.create(7L, 101L, 31L, 0),
			AnnouncementDocument.create(7L, 101L, 32L, 1)));
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L, 32L))).thenReturn(List.of(first, second));
		when(documents.findAttachedAssetIdsForOtherAnnouncements(101L, List.of(31L, 32L))).thenReturn(List.of());

		service.replace(101L, 7L, 22L, List.of(32L, 31L));

		var saved = org.mockito.ArgumentCaptor.forClass(AnnouncementDocument.class);
		verify(documents, org.mockito.Mockito.times(2)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(AnnouncementDocument::mediaAssetId).containsExactly(32L, 31L);

		when(documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(102L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L))).thenReturn(List.of(first));
		when(documents.findAttachedAssetIdsForOtherAnnouncements(102L, List.of(31L))).thenReturn(List.of());
		assertThatThrownBy(() -> service.replace(102L, 7L, 22L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_INVALID));
	}

	@Test
	void image_is_rejected_from_document_relation_and_removed_pdf_becomes_orphaned() {
		MediaAsset image = readyImage(41L, 7L, 11L);
		when(documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(41L))).thenReturn(List.of(image));
		when(documents.findAttachedAssetIdsForOtherAnnouncements(101L, List.of(41L))).thenReturn(List.of());
		assertThatThrownBy(() -> service.replace(101L, 7L, 11L, List.of(41L)))
			.isInstanceOf(BusinessException.class);
		verify(documents, never()).deleteByAnnouncementId(101L);

		MediaAsset pdf = readyPdf(31L, 7L, 11L);
		when(documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(102L))
			.thenReturn(List.of(AnnouncementDocument.create(7L, 102L, 31L, 0)));
		when(assets.findByCampusIdAndIdForUpdate(7L, 31L)).thenReturn(java.util.Optional.of(pdf));
		service.replace(102L, 7L, 22L, List.of());
		assertThat(pdf.status()).isEqualTo(MediaAssetStatus.ORPHANED);
	}

	@Test
	void orphanAll_locks_existing_pdfs_by_stable_asset_order_then_orphans_and_deletes_links() {
		MediaAsset first = readyPdf(31L, 7L, 11L);
		MediaAsset second = readyPdf(32L, 7L, 11L);
		when(documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of(
			AnnouncementDocument.create(7L, 101L, 32L, 0),
			AnnouncementDocument.create(7L, 101L, 31L, 1)));
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L, 32L))).thenReturn(List.of(first, second));

		service.orphanAll(101L, 7L);

		verify(assets).findByCampusIdAndIdInForUpdate(7L, List.of(31L, 32L));
		assertThat(first.status()).isEqualTo(MediaAssetStatus.ORPHANED);
		assertThat(second.status()).isEqualTo(MediaAssetStatus.ORPHANED);
		var ordered = inOrder(documents);
		ordered.verify(documents).deleteByAnnouncementId(101L);
		ordered.verify(documents).flush();
	}

	@Test
	void orphanAll_rejects_missing_or_non_ready_pdf_without_deleting_links() {
		MediaAsset pending = MediaAsset.reserve(7L, 11L, "application/pdf", 10, "a".repeat(64),
			"temporary/pending-document/original", Instant.parse("2026-08-04T00:00:00Z"), "notice.pdf");
		ReflectionTestUtils.setField(pending, "id", 31L);
		when(documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L))
			.thenReturn(List.of(AnnouncementDocument.create(7L, 101L, 31L, 0)));
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L))).thenReturn(List.of(pending));

		assertThatThrownBy(() -> service.orphanAll(101L, 7L))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_INVALID));

		verify(documents, never()).deleteByAnnouncementId(101L);
	}

	private MediaAsset readyPdf(Long id, Long campusId, Long ownerId) {
		MediaAsset asset = MediaAsset.reserve(campusId, ownerId, "application/pdf", 10, "a".repeat(64),
			"temporary/" + id + "/original", Instant.parse("2026-08-04T00:00:00Z"), "notice.pdf");
		ReflectionTestUtils.setField(asset, "id", id);
		asset.startProcessing();
		asset.completePdf("media/" + id + "/document.pdf", 10, "b".repeat(64));
		return asset;
	}

	private MediaAsset readyImage(Long id, Long campusId, Long ownerId) {
		MediaAsset asset = MediaAsset.reserve(campusId, ownerId, "image/jpeg", 10, "a".repeat(64),
			"temporary/" + id + "/original", Instant.parse("2026-08-04T00:00:00Z"));
		ReflectionTestUtils.setField(asset, "id", id);
		asset.startProcessing();
		asset.complete("media/t.jpg", "media/d.jpg", 10, 10, 10, "b".repeat(64));
		return asset;
	}
}
