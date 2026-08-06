package com.faithlog.weeklymaterial.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WeeklyMaterialCampusScopeContractTest {
	private static final Path MIGRATION = Path.of(
		"src/main/resources/db/migration/V22__scope_shepherd_guides_by_campus.sql");

	@Test
	void shepherdGuideHasCampusScopeWhileSharingSheetsRemainGlobal() {
		LocalDate week = LocalDate.of(2026, 8, 3);

		WeeklyMaterial guide = WeeklyMaterial.create(
			1L, week, WeeklyMaterialType.SHEPHERD_GUIDE, 10L, 100L);
		WeeklyMaterial sunday = WeeklyMaterial.create(
			1L, week, WeeklyMaterialType.SUNDAY_SHARING_SHEET, 20L, 100L);
		WeeklyMaterial saturday = WeeklyMaterial.create(
			1L, week, WeeklyMaterialType.SATURDAY_LEADER_SHARING_SHEET, 30L, 100L);

		assertThat(guide.scopeCampusId()).isEqualTo(1L);
		assertThat(sunday.scopeCampusId()).isNull();
		assertThat(saturday.scopeCampusId()).isNull();
	}

	@Test
	void v22MigratesExistingGuidesToTheirMediaCampusAndDefinesHybridUniqueSlots() throws Exception {
		String sql = Files.readString(MIGRATION);

		assertThat(sql).contains(
			"ADD COLUMN scope_campus_id",
			"WHERE material_type = 'SHEPHERD_GUIDE'",
			"scope_campus_id = media_campus_id",
			"fk_weekly_materials_scope_campus",
			"uk_weekly_materials_shepherd_slot",
			"uk_weekly_materials_global_sheet_slot",
			"SHEPHERD_GUIDE",
			"SUNDAY_SHARING_SHEET",
			"SATURDAY_LEADER_SHARING_SHEET");
	}
}
