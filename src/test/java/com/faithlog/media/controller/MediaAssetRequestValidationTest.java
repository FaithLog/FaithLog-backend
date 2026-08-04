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
}
