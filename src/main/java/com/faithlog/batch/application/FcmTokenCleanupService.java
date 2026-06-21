package com.faithlog.batch.application;

import com.faithlog.notification.domain.UserFcmToken;
import com.faithlog.notification.infrastructure.jpa.UserFcmTokenRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FcmTokenCleanupService {

	private static final Duration STALE_TOKEN_AGE = Duration.ofDays(90);

	private final UserFcmTokenRepository userFcmTokenRepository;

	public FcmTokenCleanupService(UserFcmTokenRepository userFcmTokenRepository) {
		this.userFcmTokenRepository = userFcmTokenRepository;
	}

	@Transactional
	public int deactivateStaleTokens(Instant now) {
		Instant staleThreshold = now.minus(STALE_TOKEN_AGE);
		var staleTokens = userFcmTokenRepository.findActiveStaleTokens(staleThreshold);
		staleTokens.forEach(UserFcmToken::deactivate);
		return staleTokens.size();
	}
}
