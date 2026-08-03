package com.faithlog.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.faithlog.media.service.port.MediaObjectStoragePort;
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
}
