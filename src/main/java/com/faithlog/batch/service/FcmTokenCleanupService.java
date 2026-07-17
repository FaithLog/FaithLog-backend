package com.faithlog.batch.service;

import com.faithlog.notification.domain.entity.UserFcmToken;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
		List<UserFcmToken> staleTokens = userFcmTokenRepository.findActiveStaleTokens(staleThreshold);
		staleTokens.forEach(UserFcmToken::deactivate);
		return staleTokens.size();
	}
}
