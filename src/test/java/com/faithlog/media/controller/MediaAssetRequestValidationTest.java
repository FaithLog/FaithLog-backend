package com.faithlog.media.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.media.domain.entity.MediaAsset;
import jakarta.validation.constraints.Max;
import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Test;

class MediaAssetRequestValidationTest {

	@Test
	void upload_reservation_rejects_input_larger_than_five_mib_at_http_boundary() {
		RecordComponent byteSize = MediaAssetController.UploadReservationRequest.class
			.getRecordComponents()[1];

		assertThat(byteSize.getAnnotation(Max.class))
			.isNotNull()
			.extracting(Max::value)
			.isEqualTo(MediaAsset.MAX_INPUT_BYTES);
	}
}
