package com.faithlog.user.service.port;

import com.faithlog.global.security.SessionRevocationChecker;
import java.time.Duration;

public interface SessionRevocationStore extends SessionRevocationChecker {

	void revoke(Long userId, String sessionId, Duration ttl);
}
