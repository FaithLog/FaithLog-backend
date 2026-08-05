package com.faithlog.media.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.media.domain.entity.MediaAsset;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class MediaAssetRequestValidationTest {

	@Test
	void upload_reservation_rejects_input_larger_than_five_mib_at_http_boundary() {
		var request = new MediaAssetController.UploadReservationRequest(
			"image/jpeg", MediaAsset.MAX_INPUT_BYTES + 1, "a".repeat(64));

		try (var factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(request))
				.extracting(violation -> violation.getPropertyPath().toString())
				.containsExactly("byteSize");
		}
	}

	@Test
	void pdf_accepts_exactly_thirty_mib_and_requires_a_file_name() {
		var valid = new MediaAssetController.UploadReservationRequest(
			"application/pdf", MediaAsset.MAX_PDF_INPUT_BYTES, "a".repeat(64), "주보.pdf");
		var missingName = new MediaAssetController.UploadReservationRequest(
			"application/pdf", 1024, "a".repeat(64), null);

		try (var factory = Validation.buildDefaultValidatorFactory()) {
			assertThat(factory.getValidator().validate(valid)).isEmpty();
			assertThat(factory.getValidator().validate(missingName))
				.extracting(violation -> violation.getPropertyPath().toString())
				.contains("fileName");
		}
	}
}
