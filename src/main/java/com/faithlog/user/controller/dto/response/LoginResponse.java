package com.faithlog.user.controller.dto.response;

import com.faithlog.user.service.result.LoginResult;

public record LoginResponse(
	UserMeResponse user,
	String accessToken,
	String refreshToken,
	long accessTokenExpiresIn,
	long refreshTokenExpiresIn,
	String tokenType
) {

	public static LoginResponse from(LoginResult result) {
		return new LoginResponse(
			UserMeResponse.from(result.user()),
			result.accessToken(),
			result.refreshToken(),
			result.accessTokenExpiresIn(),
			result.refreshTokenExpiresIn(),
			result.tokenType()
		);
	}
}
