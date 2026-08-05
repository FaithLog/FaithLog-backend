package com.faithlog.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.service.port.MediaObjectStoragePort;
import com.faithlog.media.domain.type.MediaAssetKind;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaAssetQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
	@Mock private MediaAssetAccessSnapshotService snapshots;
	@Mock private MediaObjectStoragePort storage;

	@Test
	void access_urls_preserve_request_order_and_include_immutable_sha256() {
		when(snapshots.authorize(7L, 11L, List.of(32L, 31L))).thenReturn(List.of(
			new MediaAssetAccessSnapshotService.AccessSnapshot(
				32L, "c".repeat(64), "private/32/thumbnail", "private/32/detail"),
			new MediaAssetAccessSnapshotService.AccessSnapshot(
				31L, "b".repeat(64), "private/31/thumbnail", "private/31/detail")));
		when(storage.presignDownload("private/32/thumbnail")).thenReturn(URI.create("https://example/32-thumb"));
		when(storage.presignDownload("private/32/detail")).thenReturn(URI.create("https://example/32-detail"));
		when(storage.presignDownload("private/31/thumbnail")).thenReturn(URI.create("https://example/31-thumb"));
		when(storage.presignDownload("private/31/detail")).thenReturn(URI.create("https://example/31-detail"));
		var service = new MediaAssetQueryService(snapshots, storage, Clock.fixed(NOW, ZoneOffset.UTC));

		var result = service.getAccessUrls(7L, 11L, List.of(32L, 31L));

		assertThat(result).extracting(item -> item.assetId()).containsExactly(32L, 31L);
		assertThat(result).extracting(item -> item.sha256()).containsExactly("c".repeat(64), "b".repeat(64));
	}

	@Test
	void thumbnail_presign_failure_is_converted_to_typed_storage_unavailable_without_provider_details() {
		when(snapshots.authorize(7L, 11L, List.of(31L))).thenReturn(List.of(
			new MediaAssetAccessSnapshotService.AccessSnapshot(
				31L, "b".repeat(64), "private/secret-thumbnail", "private/secret-detail")));
		doThrow(new IllegalStateException("provider-secret private/secret-thumbnail"))
			.when(storage).presignDownload("private/secret-thumbnail");
		var service = new MediaAssetQueryService(snapshots, storage, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatThrownBy(() -> service.getAccessUrls(7L, 11L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class, exception -> {
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
				assertThat(exception.getMessage()).doesNotContain("provider-secret", "private/secret-thumbnail");
				assertThat(exception.getCause()).isNull();
			});
	}

	@Test
	void detail_presign_failure_is_converted_to_typed_storage_unavailable_without_provider_details() {
		when(snapshots.authorize(7L, 11L, List.of(31L))).thenReturn(List.of(
			new MediaAssetAccessSnapshotService.AccessSnapshot(
				31L, "b".repeat(64), "private/secret-thumbnail", "private/secret-detail")));
		when(storage.presignDownload("private/secret-thumbnail")).thenReturn(URI.create("https://example/thumb"));
		doThrow(new IllegalStateException("provider-secret private/secret-detail"))
			.when(storage).presignDownload("private/secret-detail");
		var service = new MediaAssetQueryService(snapshots, storage, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatThrownBy(() -> service.getAccessUrls(7L, 11L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class, exception -> {
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
				assertThat(exception.getMessage()).doesNotContain("provider-secret", "private/secret-detail");
				assertThat(exception.getCause()).isNull();
			});
	}

	@Test
	void pdf_access_returns_only_an_attachment_download_url_with_original_file_name() {
		when(snapshots.authorize(7L, 11L, List.of(41L))).thenReturn(List.of(
			new MediaAssetAccessSnapshotService.AccessSnapshot(41L, MediaAssetKind.PDF, "application/pdf",
				"주보.pdf", 1234L, "d".repeat(64), null, null, "private/41/document.pdf")));
		when(storage.presignDownload("private/41/document.pdf", "주보.pdf", "application/pdf"))
			.thenReturn(URI.create("https://example/document"));
		var service = new MediaAssetQueryService(snapshots, storage, Clock.fixed(NOW, ZoneOffset.UTC));

		var result = service.getAccessUrls(7L, 11L, List.of(41L)).getFirst();

		assertThat(result.assetKind()).isEqualTo(MediaAssetKind.PDF);
		assertThat(result.fileName()).isEqualTo("주보.pdf");
		assertThat(result.downloadUrl()).isEqualTo(URI.create("https://example/document"));
		assertThat(result.thumbnailUrl()).isNull();
		assertThat(result.detailUrl()).isNull();
	}
}
