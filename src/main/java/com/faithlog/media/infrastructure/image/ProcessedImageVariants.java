package com.faithlog.media.infrastructure.image;

public record ProcessedImageVariants(
	byte[] thumbnailBytes,
	byte[] detailBytes,
	int sourceWidth,
	int sourceHeight,
	int thumbnailWidth,
	int thumbnailHeight,
	int detailWidth,
	int detailHeight,
	String outputContentType
) {
	public ProcessedImageVariants {
		thumbnailBytes = thumbnailBytes.clone();
		detailBytes = detailBytes.clone();
	}

	@Override
	public byte[] thumbnailBytes() {
		return thumbnailBytes.clone();
	}

	@Override
	public byte[] detailBytes() {
		return detailBytes.clone();
	}
}
