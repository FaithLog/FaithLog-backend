package com.faithlog.user.application;

import com.faithlog.global.security.JwtProvider.IssuedTokens;

public record TokenResult(
	String accessToken,
	String refreshToken,
	long accessTokenExpiresIn,
	long refreshTokenExpiresIn,
	String tokenType
) {

	public static TokenResult from(IssuedTokens tokens) {
		return new TokenResult(
			tokens.accessToken(),
			tokens.refreshToken(),
			tokens.accessTokenExpiresIn(),
			tokens.refreshTokenExpiresIn(),
			"Bearer"
		);
	}
}
