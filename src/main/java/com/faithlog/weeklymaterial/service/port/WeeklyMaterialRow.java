package com.faithlog.weeklymaterial.service.port;

import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import java.time.Instant;
import java.time.LocalDate;

public record WeeklyMaterialRow(Long materialId, LocalDate weekStartDate, WeeklyMaterialType materialType,
	Long assetId, String originalFileName, Long byteSize, String sha256, Instant updatedAt) {
}
