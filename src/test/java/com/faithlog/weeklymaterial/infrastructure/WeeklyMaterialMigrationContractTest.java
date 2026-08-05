package com.faithlog.weeklymaterial.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WeeklyMaterialMigrationContractTest {
	private static final Path V20 = Path.of(
		"src/main/resources/db/migration/V20__add_weekly_materials_and_notification_outbox.sql");

	@Test
	void v20DefinesTenantSafeTombstonesOutboxDedupeIndexesAndRls() throws Exception {
		assertThat(V20).exists();
		String sql = Files.readString(V20);

		assertThat(sql).contains(
			"CREATE TABLE weekly_materials",
			"media_asset_id BIGINT,",
			"UNIQUE (campus_id, week_start_date, material_type)",
			"FOREIGN KEY (campus_id, media_asset_id) REFERENCES media_assets (campus_id, id)",
			"FOREIGN KEY (uploaded_by) REFERENCES users (id)",
			"material_type IN ('SHEPHERD_GUIDE', 'SHARING_SHEET')",
			"status IN ('ACTIVE', 'DELETED')",
			"(status = 'ACTIVE' AND media_asset_id IS NOT NULL)",
			"(status = 'DELETED' AND media_asset_id IS NULL)",
			"CREATE TABLE weekly_material_notification_outbox",
			"UNIQUE (campus_id, week_start_date, material_type)",
			"FOREIGN KEY (campus_id, weekly_material_id)",
			"CREATE INDEX idx_weekly_materials_campus_week",
			"CREATE INDEX idx_weekly_material_outbox_pending",
			"ENABLE ROW LEVEL SECURITY"
		);
		assertThat(sql).doesNotContain("object_key", "file_content", "public_url");
	}
}
