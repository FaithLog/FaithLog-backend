package com.faithlog.user.presentation.dto;

import com.faithlog.user.application.TokenResult;

public record TokenResponse(
	String accessToken,
	String refreshToken,
	long accessTokenExpiresIn,
	long refreshTokenExpiresIn,
	String tokenType
) {

	public static TokenResponse from(TokenResult result) {
		return new TokenResponse(
			result.accessToken(),
			result.refreshToken(),
			result.accessTokenExpiresIn(),
			result.refreshTokenExpiresIn(),
			result.tokenType()
		);
	}
}
