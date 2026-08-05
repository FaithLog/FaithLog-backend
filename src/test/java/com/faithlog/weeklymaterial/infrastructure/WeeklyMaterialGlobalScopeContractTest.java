package com.faithlog.weeklymaterial.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.weeklymaterial.controller.dto.response.WeeklyMaterialWeekResponse;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WeeklyMaterialGlobalScopeContractTest {
	private static final Path V21 = Path.of(
		"src/main/resources/db/migration/V21__globalize_weekly_materials.sql");

	@Test
	void exposesThreeGlobalWeeklyMaterialTypesAndResponseSlots() {
		assertThat(Arrays.stream(WeeklyMaterialType.values()).map(Enum::name))
			.containsExactly("SHEPHERD_GUIDE", "SUNDAY_SHARING_SHEET", "SATURDAY_LEADER_SHARING_SHEET");

		assertThat(Arrays.stream(WeeklyMaterialWeekResponse.class.getRecordComponents())
			.map(component -> component.getName()))
			.containsExactly("weekStartDate", "shepherdGuide", "sundaySharingSheet",
				"saturdayLeaderSharingSheet");
	}

	@Test
	void v21SeparatesGlobalSlotsFromMediaTenantAndMigratesLegacySheetFailClosed() throws Exception {
		assertThat(V21).exists();
		String sql = Files.readString(V21);

		assertThat(sql).contains(
			"SHARING_SHEET",
			"SUNDAY_SHARING_SHEET",
			"SATURDAY_LEADER_SHARING_SHEET",
			"media_campus_id",
			"UNIQUE (week_start_date, material_type)",
			"FOREIGN KEY (media_campus_id, media_asset_id)",
			"weekly_material_global_lock",
			"RAISE EXCEPTION",
			"ERRCODE = '23505'"
		);
		assertThat(sql).doesNotContain("UNIQUE (campus_id, week_start_date, material_type)");
		assertThat(sql).contains("UNIQUE (week_start_date, material_type)")
			.doesNotContain("UNIQUE (campus_id, week_start_date, material_type)");
	}

	@Test
	void repositoryQueriesGlobalSlotsAndNotificationTargetsAllActiveCampusesOnlyForSunday() throws Exception {
		String repository = Files.readString(Path.of(
			"src/main/java/com/faithlog/weeklymaterial/infrastructure/repository/WeeklyMaterialRepository.java"));
		String publication = Files.readString(Path.of(
			"src/main/java/com/faithlog/weeklymaterial/service/WeeklyMaterialFirstPublication.java"));
		String recipients = Files.readString(Path.of(
			"src/main/java/com/faithlog/weeklymaterial/infrastructure/adapter/WeeklyMaterialRecipientAdapter.java"));
		String outboxes = Files.readString(Path.of(
			"src/main/java/com/faithlog/weeklymaterial/infrastructure/repository/WeeklyMaterialNotificationOutboxRepository.java"));

		assertThat(repository).contains("findSlotForUpdate(@Param(\"weekStartDate\")");
		assertThat(repository).doesNotContain("material.campusId = :campusId");
		assertThat(publication).contains("WeeklyMaterialType.SUNDAY_SHARING_SHEET");
		assertThat(publication).doesNotContain("WeeklyMaterialType.SHARING_SHEET");
		assertThat(recipients).contains("findAllActiveRecipients");
		assertThat(recipients).contains("distinct");
		assertThat(outboxes).contains("outbox.weekStartDate = :weekStartDate")
			.doesNotContain("outbox.campusId = :campusId");
	}
}
