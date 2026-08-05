package com.faithlog.media.service.port;

public interface PdfDocumentValidatorPort {
	ValidatedPdf validate(byte[] source, String declaredContentType);

	record ValidatedPdf(int pageCount) {
		public ValidatedPdf {
			if (pageCount < 1) {
				throw new IllegalArgumentException("PDF must contain at least one page");
		}
	}
}
}
