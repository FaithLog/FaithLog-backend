package com.faithlog.weeklymaterial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialAccessPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialAttachmentConflictPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialSlotLockPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WeeklyMaterialCommandServiceTest {
	private final WeeklyMaterialRepositoryPort materials = mock(WeeklyMaterialRepositoryPort.class);
	private final MediaAssetRepositoryPort assets = mock(MediaAssetRepositoryPort.class);
	private final WeeklyMaterialAccessPort access = mock(WeeklyMaterialAccessPort.class);
	private final WeeklyMaterialAttachmentConflictPort conflicts = mock(WeeklyMaterialAttachmentConflictPort.class);
	private final WeeklyMaterialCommandService service =
		new WeeklyMaterialCommandService(materials, assets, access, conflicts);

	@Test
	void firstRegistrationSerializesTheSlotAndRecordsPublicationInTheSameFlow() {
		var slotLocks = mock(WeeklyMaterialSlotLockPort.class);
		var publications = mock(WeeklyMaterialFirstPublication.class);
		var command = new WeeklyMaterialCommandService(materials, assets, access, conflicts, slotLocks, publications);
		LocalDate week = LocalDate.of(2026, 8, 3);
		MediaAsset pdf = readyPdf(20L, 1L, 100L);
		when(materials.findSlotForUpdate(1L, week, WeeklyMaterialType.SHARING_SHEET)).thenReturn(Optional.empty());
		when(assets.findByCampusIdAndIdInForUpdate(1L, List.of(20L))).thenReturn(List.of(pdf));
		when(conflicts.findAttachedAssetIds(List.of(20L))).thenReturn(List.of());
		when(materials.findAttachedAssetIds(List.of(20L))).thenReturn(List.of());
		when(materials.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
			WeeklyMaterial saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 10L);
			return saved;
		});

		WeeklyMaterial saved = command.put(1L, week, WeeklyMaterialType.SHARING_SHEET, 20L, 100L);

		var order = inOrder(slotLocks, materials, publications);
		order.verify(slotLocks).lockCampus(1L);
		order.verify(materials).findSlotForUpdate(1L, week, WeeklyMaterialType.SHARING_SHEET);
		order.verify(publications).recordFirstRegistration(saved, true);
	}

	@Test
	void firstGuideRegistrationLocksReadyOwnedPdfAndSavesOneSlotWithoutNetwork() {
		MediaAsset pdf = readyPdf(20L, 1L, 100L);
		LocalDate week = LocalDate.of(2026, 8, 3);
		when(materials.findSlotForUpdate(1L, week, WeeklyMaterialType.SHEPHERD_GUIDE))
			.thenReturn(Optional.empty());
		when(assets.findByCampusIdAndIdInForUpdate(1L, List.of(20L))).thenReturn(List.of(pdf));
		when(conflicts.findAttachedAssetIds(List.of(20L))).thenReturn(List.of());
		when(materials.findAttachedAssetIds(List.of(20L))).thenReturn(List.of());
		when(materials.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

		WeeklyMaterial result = service.put(1L, week, WeeklyMaterialType.SHEPHERD_GUIDE, 20L, 100L);

		assertThat(result.mediaAssetId()).isEqualTo(20L);
		verify(access).requireManager(1L, 100L);
		verify(assets, never()).findByCampusIdAndIdForUpdate(org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void replacementLocksOldAndNewMediaInAscendingOrderAndOrphansOnlyOldPdf() {
		LocalDate week = LocalDate.of(2026, 8, 3);
		WeeklyMaterial current = WeeklyMaterial.create(
			1L, week, WeeklyMaterialType.SHARING_SHEET, 30L, 100L);
		ReflectionTestUtils.setField(current, "id", 5L);
		MediaAsset newPdf = readyPdf(20L, 1L, 100L);
		MediaAsset oldPdf = readyPdf(30L, 1L, 100L);
		when(materials.findSlotForUpdate(1L, week, WeeklyMaterialType.SHARING_SHEET))
			.thenReturn(Optional.of(current));
		when(assets.findByCampusIdAndIdInForUpdate(1L, List.of(20L, 30L)))
			.thenReturn(List.of(newPdf, oldPdf));
		when(conflicts.findAttachedAssetIds(List.of(20L))).thenReturn(List.of());
		when(materials.findAttachedAssetIdsExcludingMaterialId(List.of(20L), 5L)).thenReturn(List.of());

		service.put(1L, week, WeeklyMaterialType.SHARING_SHEET, 20L, 100L);

		assertThat(current.mediaAssetId()).isEqualTo(20L);
		assertThat(oldPdf.status().name()).isEqualTo("ORPHANED");
		assertThat(newPdf.status().name()).isEqualTo("READY");
		var order = inOrder(materials, assets);
		order.verify(materials).findSlotForUpdate(1L, week, WeeklyMaterialType.SHARING_SHEET);
		order.verify(assets).findByCampusIdAndIdInForUpdate(1L, List.of(20L, 30L));
	}

	@Test
	void manualDeleteSuppressesPendingPublicationBeforeLockingAndOrphaningMedia() {
		var slotLocks = mock(WeeklyMaterialSlotLockPort.class);
		var publications = mock(WeeklyMaterialFirstPublication.class);
		var command = new WeeklyMaterialCommandService(materials, assets, access, conflicts, slotLocks, publications);
		LocalDate week = LocalDate.of(2026, 8, 3);
		WeeklyMaterial current = WeeklyMaterial.create(
			1L, week, WeeklyMaterialType.SHARING_SHEET, 20L, 100L);
		ReflectionTestUtils.setField(current, "id", 10L);
		MediaAsset pdf = readyPdf(20L, 1L, 100L);
		when(materials.findSlotForUpdate(1L, week, WeeklyMaterialType.SHARING_SHEET))
			.thenReturn(Optional.of(current));
		when(assets.findByCampusIdAndIdInForUpdate(1L, List.of(20L))).thenReturn(List.of(pdf));

		command.delete(1L, week, WeeklyMaterialType.SHARING_SHEET, 100L);

		var order = inOrder(slotLocks, materials, publications, assets);
		order.verify(slotLocks).lockCampus(1L);
		order.verify(materials).findSlotForUpdate(1L, week, WeeklyMaterialType.SHARING_SHEET);
		order.verify(publications).suppressPending(current);
		order.verify(assets).findByCampusIdAndIdInForUpdate(1L, List.of(20L));
		assertThat(current.status().name()).isEqualTo("DELETED");
	}

	private static MediaAsset readyPdf(Long id, Long campusId, Long ownerId) {
		MediaAsset asset = MediaAsset.reserve(campusId, ownerId, "application/pdf", 100,
			"a".repeat(64), "tmp/" + id, Instant.parse("2026-08-05T00:00:00Z"), "weekly.pdf");
		ReflectionTestUtils.setField(asset, "id", id);
		asset.startProcessing();
		asset.completePdf("private/" + id, 100, "b".repeat(64));
		return asset;
	}
}
