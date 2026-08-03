package com.faithlog.media.infrastructure.redis;

import com.faithlog.media.service.port.MediaUploadRateLimitPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class UnavailableMediaUploadRateLimitConfig {

	@Bean
	@ConditionalOnMissingBean(MediaUploadRateLimitPort.class)
	MediaUploadRateLimitPort unavailableMediaUploadRateLimitPort() {
		return (campusId, userId) -> false;
	}
}
