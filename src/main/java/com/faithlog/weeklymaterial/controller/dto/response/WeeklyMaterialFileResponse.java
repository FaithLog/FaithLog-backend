package com.faithlog.weeklymaterial.controller.dto.response;

import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.result.WeeklyMaterialFileResult;
import java.time.Instant;

public record WeeklyMaterialFileResponse(Long assetId, WeeklyMaterialType materialType, String originalFileName,
	Long byteSize, String sha256, Instant updatedAt) {
	public static WeeklyMaterialFileResponse from(WeeklyMaterialFileResult result) {
		return result == null ? null : new WeeklyMaterialFileResponse(result.assetId(), result.materialType(),
			result.originalFileName(), result.byteSize(), result.sha256(), result.updatedAt());
	}
}
