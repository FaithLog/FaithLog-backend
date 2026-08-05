package com.faithlog.media.service.result;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.domain.type.MediaAssetKind;

public record MediaAssetResult(
	Long assetId,
	Long campusId,
	MediaAssetStatus status,
	MediaAssetKind assetKind,
	String contentType,
	String fileName,
	String sha256,
	Integer width,
	Integer height,
	Long byteSize
) {
	public MediaAssetResult(
		Long assetId, Long campusId, MediaAssetStatus status, String sha256,
		Integer width, Integer height, Long byteSize
	) {
		this(assetId, campusId, status, MediaAssetKind.IMAGE, "image/jpeg", null,
			sha256, width, height, byteSize);
	}

	public static MediaAssetResult from(MediaAsset asset) {
		return new MediaAssetResult(asset.id(), asset.campusId(), asset.status(), asset.kind(),
			asset.inputContentType(), asset.originalFileName(), asset.outputSha256(),
			asset.width(), asset.height(), asset.outputByteSize());
	}
}
