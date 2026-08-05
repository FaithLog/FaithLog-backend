package com.faithlog.weeklymaterial.service.port;

import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import java.time.Instant;
import java.time.LocalDate;

public record WeeklyMaterialOutboxSnapshot(
	Long id,
	Long campusId,
	LocalDate weekStartDate,
	WeeklyMaterialType materialType,
	Instant processedAt
) {
	public boolean isProcessed() {
		return processedAt != null;
	}
}
