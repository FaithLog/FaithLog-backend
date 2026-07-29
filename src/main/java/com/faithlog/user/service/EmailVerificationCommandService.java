package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.ConfirmEmailVerificationCommand;
import com.faithlog.user.service.command.RequestEmailVerificationCommand;
import com.faithlog.user.service.policy.EmailVerificationPolicy;
import com.faithlog.user.service.port.EmailSenderPort;
import com.faithlog.user.service.port.EmailDeliveryException;
import com.faithlog.user.service.port.EmailVerificationStore;
import com.faithlog.user.service.port.EmailVerificationStore.ChallengeIssueResult;
import com.faithlog.user.service.port.EmailVerificationStore.ChallengeVerificationResult;
import com.faithlog.user.service.port.EmailVerificationStoreException;
import com.faithlog.user.service.port.OneTimeTokenGenerator;
import com.faithlog.user.service.port.VerificationCodeGenerator;
import com.faithlog.user.service.result.EmailVerificationRequestResult;
import com.faithlog.user.service.result.EmailVerificationResult;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationCommandService {

	private final UserRepository userRepository;
	private final EmailVerificationStore verificationStore;
	private final EmailSenderPort emailSenderPort;
	private final VerificationCodeGenerator codeGenerator;
	private final OneTimeTokenGenerator tokenGenerator;
	private final EmailVerificationPolicy policy;

	public EmailVerificationCommandService(
		UserRepository userRepository,
		EmailVerificationStore verificationStore,
		EmailSenderPort emailSenderPort,
		VerificationCodeGenerator codeGenerator,
		OneTimeTokenGenerator tokenGenerator,
		EmailVerificationPolicy policy
	) {
		this.userRepository = userRepository;
		this.verificationStore = verificationStore;
		this.emailSenderPort = emailSenderPort;
		this.codeGenerator = codeGenerator;
		this.tokenGenerator = tokenGenerator;
		this.policy = policy;
	}

	public EmailVerificationRequestResult requestSignup(RequestEmailVerificationCommand command) {
		String email = EmailNormalizer.normalize(command.email());
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}
		return issueChallenge(EmailVerificationPurpose.SIGNUP, email, true);
	}

	public EmailVerificationResult confirmSignup(ConfirmEmailVerificationCommand command) {
		String email = EmailNormalizer.normalize(command.email());
		return confirmChallenge(EmailVerificationPurpose.SIGNUP, email, command.code(), email);
	}

	public EmailVerificationRequestResult requestPasswordReset(RequestEmailVerificationCommand command) {
		String email = EmailNormalizer.normalize(command.email());
		Optional<User> user = userRepository.findByEmail(email).filter(User::isActive);
		return issueChallenge(EmailVerificationPurpose.PASSWORD_RESET, email, user.isPresent());
	}

	public EmailVerificationResult confirmPasswordReset(ConfirmEmailVerificationCommand command) {
		String email = EmailNormalizer.normalize(command.email());
		Optional<User> user = userRepository.findByEmail(email).filter(User::isActive);
		String subject = user.map(value -> String.valueOf(value.id())).orElse("missing");
		EmailVerificationResult result = confirmChallenge(
			EmailVerificationPurpose.PASSWORD_RESET,
			email,
			command.code(),
			subject
		);
		if (user.isEmpty()) {
			discardPasswordResetGrant(result.token());
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_CODE_INVALID);
		}
		return result;
	}

	private EmailVerificationRequestResult issueChallenge(
		EmailVerificationPurpose purpose,
		String email,
		boolean sendEmail
	) {
		String code = codeGenerator.generate();
		ChallengeIssueResult issueResult;
		try {
			issueResult = verificationStore.issueChallenge(purpose, email, code, policy);
		} catch (EmailVerificationStoreException exception) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_UNAVAILABLE);
		}
		if (issueResult == ChallengeIssueResult.COOLDOWN) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_RESEND_THROTTLED);
		}
		if (issueResult == ChallengeIssueResult.RATE_LIMITED) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_RATE_LIMITED);
		}
		if (sendEmail) {
			sendVerificationCode(purpose, email, code);
		}
		return requestResult();
	}

	private void sendVerificationCode(EmailVerificationPurpose purpose, String email, String code) {
		try {
			emailSenderPort.sendVerificationCode(purpose, email, code, policy.challengeTtl());
		} catch (EmailDeliveryException exception) {
			if (purpose == EmailVerificationPurpose.SIGNUP) {
				cancelChallenge(purpose, email, code);
				throw new BusinessException(ErrorCode.AUTH_EMAIL_DELIVERY_UNAVAILABLE);
			}
		}
	}

	private EmailVerificationResult confirmChallenge(
		EmailVerificationPurpose purpose,
		String email,
		String code,
		String subject
	) {
		String token = tokenGenerator.generate();
		ChallengeVerificationResult result;
		try {
			result = verificationStore.confirmChallenge(
				purpose,
				email,
				code,
				token,
				subject,
				policy
			);
		} catch (EmailVerificationStoreException exception) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_UNAVAILABLE);
		}
		if (result == ChallengeVerificationResult.INVALID) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_CODE_INVALID);
		}
		if (result == ChallengeVerificationResult.EXPIRED) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_CODE_EXPIRED);
		}
		if (result == ChallengeVerificationResult.ATTEMPTS_EXCEEDED) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
		}
		if (result == ChallengeVerificationResult.GRANT_COLLISION) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_UNAVAILABLE);
		}
		return new EmailVerificationResult(token, policy.grantTtl().toSeconds());
	}

	private EmailVerificationRequestResult requestResult() {
		return new EmailVerificationRequestResult(
			policy.challengeTtl().toSeconds(),
			policy.resendCooldown().toSeconds()
		);
	}

	private void cancelChallenge(EmailVerificationPurpose purpose, String email, String code) {
		try {
			verificationStore.cancelChallenge(purpose, email, code);
		} catch (EmailVerificationStoreException exception) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_UNAVAILABLE);
		}
	}

	private void discardPasswordResetGrant(String token) {
		try {
			verificationStore.consumePasswordResetGrant(token);
		} catch (EmailVerificationStoreException exception) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_UNAVAILABLE);
		}
	}
}
