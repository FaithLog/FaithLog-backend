package com.faithlog.weeklymaterial.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialStatus;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WeeklyMaterialTest {

	@Test
	void materialTypesAreExact() {
		assertThat(WeeklyMaterialType.values())
			.extracting(Enum::name)
			.containsExactly("SHEPHERD_GUIDE", "SHARING_SHEET");
	}

	@Test
	void createReplaceDeleteAndReregisterKeepOneIndependentSlot() {
		WeeklyMaterial material = WeeklyMaterial.create(
			1L, LocalDate.of(2026, 8, 3), WeeklyMaterialType.SHEPHERD_GUIDE, 10L, 100L);

		assertThat(material.status()).isEqualTo(WeeklyMaterialStatus.ACTIVE);
		assertThat(material.mediaAssetId()).isEqualTo(10L);
		assertThat(material.replaceMedia(11L, 101L)).isEqualTo(10L);
		assertThat(material.mediaAssetId()).isEqualTo(11L);
		assertThat(material.uploadedBy()).isEqualTo(101L);

		assertThat(material.delete()).isEqualTo(11L);
		assertThat(material.status()).isEqualTo(WeeklyMaterialStatus.DELETED);

		material.reregister(12L, 102L);
		assertThat(material.status()).isEqualTo(WeeklyMaterialStatus.ACTIVE);
		assertThat(material.mediaAssetId()).isEqualTo(12L);
		assertThat(material.uploadedBy()).isEqualTo(102L);
	}
}
