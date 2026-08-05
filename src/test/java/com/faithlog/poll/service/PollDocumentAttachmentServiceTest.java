package com.faithlog.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.poll.domain.entity.PollDocument;
import com.faithlog.poll.infrastructure.repository.PollDocumentRepository;
import com.faithlog.poll.infrastructure.repository.PollImageRepository;
import com.faithlog.poll.service.port.AnnouncementMediaAttachmentPort;
import com.faithlog.poll.service.port.WeeklyMaterialMediaAttachmentPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PollDocumentAttachmentServiceTest {

	@Mock private PollDocumentRepository documents;
	@Mock private PollImageRepository images;
	@Mock private MediaAssetRepositoryPort assets;
	@Mock private AnnouncementMediaAttachmentPort announcementAttachments;
	@Mock private WeeklyMaterialMediaAttachmentPort weeklyAttachments;
	private PollDocumentAttachmentService service;

	@BeforeEach
	void setUp() {
		service = new PollDocumentAttachmentService(
			documents, images, assets, announcementAttachments, weeklyAttachments);
	}

	@Test
	void weeklyPdfIsRejectedAfterMediaLockBeforeRelationMutation() {
		MediaAsset pdf = readyPdf(31L, 7L, 11L);
		when(documents.findByPollIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L))).thenReturn(List.of(pdf));
		when(weeklyAttachments.findAttachedAssetIds(List.of(31L))).thenReturn(List.of(31L));

		assertThatThrownBy(() -> service.replace(101L, 7L, 11L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_STATE_CONFLICT));
		var order = org.mockito.Mockito.inOrder(assets, weeklyAttachments);
		order.verify(assets).findByCampusIdAndIdInForUpdate(7L, List.of(31L));
		order.verify(weeklyAttachments).findAttachedAssetIds(List.of(31L));
		verify(documents, never()).deleteByPollId(101L);
	}

	@Test
	void duty_editor_can_keep_existing_other_owner_pdf_and_add_only_own_pdf() {
		MediaAsset existing = readyPdf(31L, 7L, 11L);
		MediaAsset owned = readyPdf(32L, 7L, 22L);
		when(documents.findByPollIdOrderByDisplayOrderAscIdAsc(101L))
			.thenReturn(List.of(PollDocument.create(7L, 101L, 31L, 0)));
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L, 32L))).thenReturn(List.of(existing, owned));
		when(documents.findAttachedAssetIdsForOtherPolls(101L, List.of(31L, 32L))).thenReturn(List.of());

		service.replace(101L, 7L, 22L, List.of(31L, 32L));
		verify(documents, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any());

		when(documents.findByPollIdOrderByDisplayOrderAscIdAsc(102L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L))).thenReturn(List.of(existing));
		when(documents.findAttachedAssetIdsForOtherPolls(102L, List.of(31L))).thenReturn(List.of());
		assertThatThrownBy(() -> service.replace(102L, 7L, 22L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_INVALID));
	}

	@Test
	void image_is_rejected_and_removed_pdf_is_orphaned() {
		MediaAsset image = readyImage(41L, 7L, 11L);
		when(documents.findByPollIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(41L))).thenReturn(List.of(image));
		when(documents.findAttachedAssetIdsForOtherPolls(101L, List.of(41L))).thenReturn(List.of());
		assertThatThrownBy(() -> service.replace(101L, 7L, 11L, List.of(41L)))
			.isInstanceOf(BusinessException.class);
		verify(documents, never()).deleteByPollId(101L);

		MediaAsset pdf = readyPdf(31L, 7L, 11L);
		when(documents.findByPollIdOrderByDisplayOrderAscIdAsc(102L))
			.thenReturn(List.of(PollDocument.create(7L, 102L, 31L, 0)));
		when(assets.findByCampusIdAndIdForUpdate(7L, 31L)).thenReturn(java.util.Optional.of(pdf));
		service.replace(102L, 7L, 22L, List.of());
		assertThat(pdf.status()).isEqualTo(MediaAssetStatus.ORPHANED);
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
