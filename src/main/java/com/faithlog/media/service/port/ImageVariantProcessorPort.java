package com.faithlog.media.service.port;

public interface ImageVariantProcessorPort {

	ProcessedVariants process(byte[] source, String declaredContentType);

	record ProcessedVariants(
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
		public ProcessedVariants {
			thumbnailBytes = thumbnailBytes.clone();
			detailBytes = detailBytes.clone();
		}
		@Override public byte[] thumbnailBytes() { return thumbnailBytes.clone(); }
		@Override public byte[] detailBytes() { return detailBytes.clone(); }
	}
}
