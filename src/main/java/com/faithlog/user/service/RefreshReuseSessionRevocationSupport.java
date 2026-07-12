package com.faithlog.user.service;

import com.faithlog.user.service.port.SessionRevocationStore;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class RefreshReuseSessionRevocationSupport {

	private static final long SAFETY_MARGIN_SECONDS = 60;

	private final SessionRevocationStore sessionRevocationStore;
	private final Duration revocationTtl;

	RefreshReuseSessionRevocationSupport(
		SessionRevocationStore sessionRevocationStore,
		@Value("${faithlog.jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds
	) {
		this.sessionRevocationStore = sessionRevocationStore;
		this.revocationTtl = Duration.ofSeconds(Math.addExact(refreshTokenValiditySeconds, SAFETY_MARGIN_SECONDS));
	}

	void revoke(Long userId, String sessionId) {
		sessionRevocationStore.revoke(userId, sessionId, revocationTtl);
	}
}
