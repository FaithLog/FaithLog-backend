package com.faithlog.media.service.result;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;

public record MediaAssetResult(
	Long assetId,
	Long campusId,
	MediaAssetStatus status,
	String sha256,
	Integer width,
	Integer height,
	Long byteSize
) {
	public static MediaAssetResult from(MediaAsset asset) {
		return new MediaAssetResult(asset.id(), asset.campusId(), asset.status(), asset.outputSha256(),
			asset.width(), asset.height(), asset.outputByteSize());
	}
}
