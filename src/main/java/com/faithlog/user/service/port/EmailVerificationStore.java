package com.faithlog.user.service.port;

import com.faithlog.user.service.EmailVerificationPolicy;
import com.faithlog.user.service.EmailVerificationPurpose;
import java.util.OptionalLong;

public interface EmailVerificationStore {

	ChallengeIssueResult issueChallenge(
		EmailVerificationPurpose purpose,
		String email,
		String code,
		EmailVerificationPolicy policy
	);

	void cancelChallenge(EmailVerificationPurpose purpose, String email, String code);

	ChallengeVerificationResult confirmChallenge(
		EmailVerificationPurpose purpose,
		String email,
		String code,
		String grantToken,
		String grantSubject,
		EmailVerificationPolicy policy
	);

	boolean consumeSignupGrant(String email, String grantToken);

	OptionalLong consumePasswordResetGrant(String grantToken);

	enum ChallengeIssueResult {
		ISSUED,
		COOLDOWN,
		RATE_LIMITED
	}

	enum ChallengeVerificationResult {
		VERIFIED,
		INVALID,
		EXPIRED,
		ATTEMPTS_EXCEEDED,
		GRANT_COLLISION
	}
}
