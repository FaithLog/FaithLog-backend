package com.faithlog.user.support;

import com.faithlog.user.service.EmailVerificationPolicy;
import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.port.EmailVerificationStore;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

public class InMemoryEmailVerificationStore implements EmailVerificationStore {

	private final Map<String, String> challenges = new HashMap<>();
	private final Map<String, String> signupGrants = new HashMap<>();
	private final Map<String, Long> passwordResetGrants = new HashMap<>();

	@Override
	public synchronized ChallengeIssueResult issueChallenge(
		EmailVerificationPurpose purpose,
		String email,
		String code,
		EmailVerificationPolicy policy
	) {
		challenges.put(challengeKey(purpose, email), code);
		return ChallengeIssueResult.ISSUED;
	}

	@Override
	public synchronized void cancelChallenge(EmailVerificationPurpose purpose, String email, String code) {
		challenges.remove(challengeKey(purpose, email), code);
	}

	@Override
	public synchronized ChallengeVerificationResult confirmChallenge(
		EmailVerificationPurpose purpose,
		String email,
		String code,
		String grantToken,
		String grantSubject,
		EmailVerificationPolicy policy
	) {
		if (!challenges.remove(challengeKey(purpose, email), code)) {
			return ChallengeVerificationResult.INVALID;
		}
		if (purpose == EmailVerificationPurpose.SIGNUP) {
			signupGrants.put(grantToken, grantSubject);
		} else {
			passwordResetGrants.put(grantToken, Long.parseLong(grantSubject));
		}
		return ChallengeVerificationResult.VERIFIED;
	}

	@Override
	public synchronized boolean consumeSignupGrant(String email, String grantToken) {
		return signupGrants.remove(grantToken, email);
	}

	@Override
	public synchronized OptionalLong consumePasswordResetGrant(String grantToken) {
		Long userId = passwordResetGrants.remove(grantToken);
		return userId == null ? OptionalLong.empty() : OptionalLong.of(userId);
	}

	public synchronized void putPasswordResetGrant(String grantToken, Long userId) {
		passwordResetGrants.put(grantToken, userId);
	}

	public synchronized void putSignupGrant(String grantToken, String email) {
		signupGrants.put(grantToken, email);
	}

	private String challengeKey(EmailVerificationPurpose purpose, String email) {
		return purpose.name() + ":" + email;
	}
}
