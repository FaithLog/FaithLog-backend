package com.faithlog.media.infrastructure.scheduler;

import com.faithlog.media.service.MediaAssetCleanupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "faithlog.scheduler", name = "enabled", havingValue = "true")
public class MediaAssetCleanupJobs {

	private final MediaAssetCleanupService cleanupService;

	public MediaAssetCleanupJobs(MediaAssetCleanupService cleanupService) {
		this.cleanupService = cleanupService;
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.media-cleanup-delay-ms:3600000}")
	public void cleanupExpiredMedia() {
		cleanupService.cleanupBatch();
	}
}
