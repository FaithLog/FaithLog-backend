package com.faithlog.user.application.port;

import com.faithlog.global.security.AccessTokenBlacklistChecker;
import java.time.Duration;

public interface AccessTokenBlacklistStore extends AccessTokenBlacklistChecker {

	void blacklist(String jti, Duration ttl);
}
