package com.faithlog.user.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.service.command.LogoutCommand;
import com.faithlog.user.service.port.CurrentDeviceFcmTokenDeactivationCommand;
import com.faithlog.user.service.port.CurrentDeviceFcmTokenDeactivationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutCommandService {

	private final UserSessionRevocationSupport sessionRevocationSupport;
	private final CurrentDeviceFcmTokenDeactivationPort fcmTokenDeactivationPort;

	public LogoutCommandService(
		UserSessionRevocationSupport sessionRevocationSupport,
		CurrentDeviceFcmTokenDeactivationPort fcmTokenDeactivationPort
	) {
		this.sessionRevocationSupport = sessionRevocationSupport;
		this.fcmTokenDeactivationPort = fcmTokenDeactivationPort;
	}

	@Transactional
	public void logout(LogoutCommand command) {
		if (command.accessJti() == null || command.sessionId() == null || command.userId() == null) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}

		sessionRevocationSupport.revokeCurrentSession(
			command.userId(),
			command.sessionId(),
			command.accessJti(),
			command.accessTokenExpiresAt()
		);
		deactivateFcmTokenIfRequested(command);
	}

	private void deactivateFcmTokenIfRequested(LogoutCommand command) {
		if (command.clientInstanceId() == null && command.fcmToken() == null) {
			return;
		}
		fcmTokenDeactivationPort.deactivateCurrentDevice(new CurrentDeviceFcmTokenDeactivationCommand(
			command.userId(),
			command.clientInstanceId(),
			command.fcmToken()
		));
	}
}
