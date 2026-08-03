package com.faithlog.media.infrastructure.redis;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.service.port.MediaUploadRateLimitPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class UnavailableMediaUploadRateLimitConfig {

	@Bean
	@ConditionalOnMissingBean(MediaUploadRateLimitPort.class)
	MediaUploadRateLimitPort unavailableMediaUploadRateLimitPort() {
		return (campusId, userId) -> {
			throw new BusinessException(ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
		};
	}
}
