package com.faithlog.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.service.policy.MediaAssetAccessPolicy;
import com.faithlog.media.service.port.AnnouncementMediaAccessPort;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.media.service.port.MediaObjectStoragePort;
import com.faithlog.media.service.port.PollMediaAccessPort;
import com.faithlog.media.service.port.WeeklyMaterialMediaAccessPort;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WeeklyMaterialMediaAssetAccessTest {
	private final AnnouncementMediaAccessPort announcements = mock(AnnouncementMediaAccessPort.class);
	private final PollMediaAccessPort polls = mock(PollMediaAccessPort.class);
	private final WeeklyMaterialMediaAccessPort weekly = mock(WeeklyMaterialMediaAccessPort.class);
	private final MediaAssetRepositoryPort assets = mock(MediaAssetRepositoryPort.class);
	private final MediaObjectStoragePort storage = mock(MediaObjectStoragePort.class);

	@Test
	void activeMemberGetsOrderedWeeklyPdfUrlsAndSha256ThroughActualQueryPath() {
		MediaAsset first = readyPdf(41L, "first.pdf");
		MediaAsset second = readyPdf(42L, "second.pdf");
		when(weekly.findActiveAttachedAssetIds(7L, List.of(42L, 41L))).thenReturn(Set.of(41L, 42L));
		when(assets.findByCampusIdAndIdIn(7L, List.of(42L, 41L))).thenReturn(List.of(first, second));
		when(storage.presignDownload("private/42", "second.pdf", "application/pdf"))
			.thenReturn(URI.create("https://example/42"));
		when(storage.presignDownload("private/41", "first.pdf", "application/pdf"))
			.thenReturn(URI.create("https://example/41"));

		var result = query().getAccessUrls(7L, 12L, List.of(42L, 41L));

		assertThat(result).extracting(item -> item.assetId()).containsExactly(42L, 41L);
		assertThat(result).extracting(item -> item.downloadUrl())
			.containsExactly(URI.create("https://example/42"), URI.create("https://example/41"));
		assertThat(result).extracting(item -> item.sha256()).containsExactly("b".repeat(64), "b".repeat(64));
		assertThat(result).allSatisfy(item -> {
			assertThat(item.thumbnailUrl()).isNull();
			assertThat(item.detailUrl()).isNull();
		});
	}

	@Test
	void inactiveOtherCampusOrDeletedWeeklyMaterialCannotPresign() {
		doThrow(new BusinessException(ErrorCode.MEDIA_ASSET_ACCESS_FORBIDDEN))
			.when(announcements).requireActiveMember(7L, 12L);
		assertThatThrownBy(() -> query().getAccessUrls(7L, 12L, List.of(41L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_ACCESS_FORBIDDEN));
		verifyNoInteractions(storage);
	}

	private MediaAssetQueryService query() {
		var policy = new MediaAssetAccessPolicy(announcements, polls, weekly);
		return new MediaAssetQueryService(new MediaAssetAccessSnapshotService(assets, policy), storage,
			Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));
	}

	private static MediaAsset readyPdf(Long id, String fileName) {
		MediaAsset asset = MediaAsset.reserve(7L, 11L, "application/pdf", 100, "a".repeat(64),
			"tmp/" + id, Instant.parse("2026-08-06T00:00:00Z"), fileName);
		ReflectionTestUtils.setField(asset, "id", id);
		asset.startProcessing();
		asset.completePdf("private/" + id, 100, "b".repeat(64));
		return asset;
	}
}
