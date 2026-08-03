package com.faithlog.media.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class UnavailableMediaUploadRateLimitConfigTest {

	@Test
	void unavailable_redis_fails_reservation_as_storage_unavailable() {
		var limiter = new UnavailableMediaUploadRateLimitConfig().unavailableMediaUploadRateLimitPort();

		assertThatThrownBy(() -> limiter.acquire(7L, 11L))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
					.isEqualTo(ErrorCode.MEDIA_STORAGE_UNAVAILABLE));
	}
}
