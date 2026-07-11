package com.faithlog.batch.service;

import com.faithlog.notification.service.FcmTokenCommandService;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class FcmTokenCleanupService {

	private final FcmTokenCommandService fcmTokenCommandService;

	public FcmTokenCleanupService(FcmTokenCommandService fcmTokenCommandService) {
		this.fcmTokenCommandService = fcmTokenCommandService;
	}

	public int deactivateStaleTokens(Instant now) {
		return fcmTokenCommandService.deactivateStaleTokens(now);
	}
}
