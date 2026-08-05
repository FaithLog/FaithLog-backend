package com.faithlog.weeklymaterial.service.result;

import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRow;
import java.time.Instant;

public record WeeklyMaterialFileResult(Long assetId, WeeklyMaterialType materialType, String originalFileName,
	Long byteSize, String sha256, Instant updatedAt) {
	public static WeeklyMaterialFileResult from(WeeklyMaterialRow row) {
		return new WeeklyMaterialFileResult(row.assetId(), row.materialType(), row.originalFileName(),
			row.byteSize(), row.sha256(), row.updatedAt());
	}
}
