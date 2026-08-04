package com.faithlog.media.service.result;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record MediaUploadReservationResult(
	Long assetId,
	URI uploadUrl,
	Map<String, String> requiredHeaders,
	Instant expiresAt
) {
	public MediaUploadReservationResult { requiredHeaders = Map.copyOf(requiredHeaders); }
}
