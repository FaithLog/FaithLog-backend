package com.faithlog.user.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.service.command.LogoutCommand;

public record LogoutRequest(
	String refreshToken,
	String clientInstanceId,
	String fcmToken
) {

	public static LogoutCommand toCommand(LogoutRequest request, AuthenticatedUser authenticatedUser) {
		LogoutRequest safeRequest = request == null ? new LogoutRequest(null, null, null) : request;
		return new LogoutCommand(
			authenticatedUser.userId(),
			authenticatedUser.sessionId(),
			authenticatedUser.jti(),
			authenticatedUser.accessTokenExpiresAt(),
			safeRequest.refreshToken(),
			safeRequest.clientInstanceId(),
			safeRequest.fcmToken()
		);
	}
}
