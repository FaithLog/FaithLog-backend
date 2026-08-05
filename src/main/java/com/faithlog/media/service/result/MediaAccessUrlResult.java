package com.faithlog.media.service.result;

import java.net.URI;
import java.time.Instant;
import com.faithlog.media.domain.type.MediaAssetKind;

public record MediaAccessUrlResult(
	Long assetId,
	MediaAssetKind assetKind,
	String contentType,
	String fileName,
	Long byteSize,
	String sha256,
	URI thumbnailUrl,
	URI detailUrl,
	URI downloadUrl,
	Instant expiresAt
) {
	public MediaAccessUrlResult(Long assetId, String sha256, URI thumbnailUrl, URI detailUrl, Instant expiresAt) {
		this(assetId, MediaAssetKind.IMAGE, "image/jpeg", null, null, sha256,
			thumbnailUrl, detailUrl, null, expiresAt);
	}
}
