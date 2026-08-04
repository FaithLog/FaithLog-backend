package com.faithlog.media.service.port;

public interface MediaUploadRateLimitPort {
	boolean acquire(Long campusId, Long userId);
}
