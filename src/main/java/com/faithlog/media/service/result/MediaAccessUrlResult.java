package com.faithlog.media.service.result;

import java.net.URI;
import java.time.Instant;

public record MediaAccessUrlResult(Long assetId, URI thumbnailUrl, URI detailUrl, Instant expiresAt) {
}
