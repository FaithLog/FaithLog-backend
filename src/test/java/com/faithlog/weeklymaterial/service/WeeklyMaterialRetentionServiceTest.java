package com.faithlog.weeklymaterial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WeeklyMaterialRetentionServiceTest {
	private final WeeklyMaterialRepositoryPort materials = mock(WeeklyMaterialRepositoryPort.class);
	private final MediaAssetRepositoryPort assets = mock(MediaAssetRepositoryPort.class);
	private final WeeklyMaterialFirstPublication publications = mock(WeeklyMaterialFirstPublication.class);
	private final WeeklyMaterialRetentionService service =
		new WeeklyMaterialRetentionService(materials, assets, publications);

	@Test
	void physicallyDeletesDueActiveRowAndHandsPdfToExistingCleanup() {
		WeeklyMaterial material = material(LocalDate.of(2026, 5, 4), 20L);
		MediaAsset pdf = readyPdf(20L);
		when(materials.findByIdForUpdate(10L)).thenReturn(Optional.of(material));
		when(assets.findByIdInForUpdate(List.of(20L))).thenReturn(List.of(pdf));

		assertThat(service.deleteIfDue(10L, LocalDate.of(2026, 8, 4))).isTrue();

		assertThat(pdf.status().name()).isEqualTo("ORPHANED");
		var order = org.mockito.Mockito.inOrder(materials, publications, assets);
		order.verify(materials).findByIdForUpdate(10L);
		order.verify(publications).suppressPending(material);
		order.verify(assets).findByIdInForUpdate(List.of(20L));
		verify(materials).delete(material);
	}

	@Test
	void physicallyDeletesDueTombstoneWithoutTouchingStorageOrMedia() {
		WeeklyMaterial material = material(LocalDate.of(2026, 5, 4), 20L);
		material.delete();
		when(materials.findByIdForUpdate(10L)).thenReturn(Optional.of(material));

		assertThat(service.deleteIfDue(10L, LocalDate.of(2026, 8, 4))).isTrue();

		verify(publications).suppressPending(material);
		verify(assets, never()).findByIdInForUpdate(org.mockito.ArgumentMatchers.anyList());
		verify(materials).delete(material);
	}

	@Test
	void rechecksExactCalendarMonthBoundaryUnderRowLock() {
		WeeklyMaterial material = material(LocalDate.of(2026, 5, 4), 20L);
		when(materials.findByIdForUpdate(10L)).thenReturn(Optional.of(material));

		assertThat(service.deleteIfDue(10L, LocalDate.of(2026, 8, 3))).isFalse();

		verify(materials, never()).delete(material);
		verify(assets, never()).findByIdInForUpdate(org.mockito.ArgumentMatchers.anyList());
	}

	private static WeeklyMaterial material(LocalDate week, Long assetId) {
		WeeklyMaterial material = WeeklyMaterial.create(1L, week, WeeklyMaterialType.SUNDAY_SHARING_SHEET, assetId, 100L);
		ReflectionTestUtils.setField(material, "id", 10L);
		return material;
	}

	private static MediaAsset readyPdf(Long id) {
		MediaAsset asset = MediaAsset.reserve(1L, 100L, "application/pdf", 100, "a".repeat(64),
			"tmp/" + id, Instant.parse("2026-08-05T00:00:00Z"), "weekly.pdf");
		ReflectionTestUtils.setField(asset, "id", id);
		asset.startProcessing();
		asset.completePdf("private/" + id, 100, "b".repeat(64));
		return asset;
	}
}
