package com.faithlog.user.service.result;

import java.time.Instant;

public record YearlyRecapStoredSnapshot(
	YearlyRecapSnapshotData data,
	Instant firstPresentedAt
) {
}
