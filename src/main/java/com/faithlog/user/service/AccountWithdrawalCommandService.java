package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.DeleteMyAccountCommand;
import com.faithlog.user.service.port.UserFcmTokenDeactivationPort;
import com.faithlog.user.service.result.DeleteMyAccountResult;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountWithdrawalCommandService {

	private static final String DELETE_CONFIRM_TEXT = "회원탈퇴";

	private final UserRepository userRepository;
	private final AccountSoftDeletionSupport softDeletionSupport;
	private final UserSessionRevocationSupport sessionRevocationSupport;
	private final UserFcmTokenDeactivationPort fcmTokenDeactivationPort;

	public AccountWithdrawalCommandService(
		UserRepository userRepository,
		AccountSoftDeletionSupport softDeletionSupport,
		UserSessionRevocationSupport sessionRevocationSupport,
		UserFcmTokenDeactivationPort fcmTokenDeactivationPort
	) {
		this.userRepository = userRepository;
		this.softDeletionSupport = softDeletionSupport;
		this.sessionRevocationSupport = sessionRevocationSupport;
		this.fcmTokenDeactivationPort = fcmTokenDeactivationPort;
	}

	@Transactional
	public DeleteMyAccountResult deleteMyAccount(DeleteMyAccountCommand command) {
		if (command.userId() == null || command.sessionId() == null || command.accessJti() == null) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		User user = userRepository.findById(command.userId())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (!user.isActive() || user.deletedAt() != null) {
			throw new BusinessException(ErrorCode.USER_ALREADY_DELETED);
		}
		if (!softDeletionSupport.passwordMatches(command.password(), user.passwordHash())) {
			throw new BusinessException(ErrorCode.USER_DELETE_PASSWORD_MISMATCH);
		}
		if (!DELETE_CONFIRM_TEXT.equals(command.confirmText())) {
			throw new BusinessException(ErrorCode.USER_DELETE_CONFIRM_TEXT_INVALID);
		}

		Instant deletedAt = Instant.now();
		softDeletionSupport.deactivateCampusMemberships(user.id());
		fcmTokenDeactivationPort.deactivateAllForUser(user.id());
		sessionRevocationSupport.revokeAllSessions(user.id(), command.accessJti(), command.accessTokenExpiresAt());
		softDeletionSupport.softDelete(user, deletedAt);

		return new DeleteMyAccountResult(deletedAt);
	}
}
