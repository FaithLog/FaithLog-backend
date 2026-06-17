package com.faithlog.user.presentation.dto;

import com.faithlog.global.security.JwtProvider.IssuedTokens;

public record LoginResponse(
	UserMeResponse user,
	String accessToken,
	String refreshToken,
	long accessTokenExpiresIn,
	long refreshTokenExpiresIn,
	String tokenType
) {

	public static LoginResponse of(UserMeResponse user, IssuedTokens tokens) {
		return new LoginResponse(
			user,
			tokens.accessToken(),
			tokens.refreshToken(),
			tokens.accessTokenExpiresIn(),
			tokens.refreshTokenExpiresIn(),
			"Bearer"
		);
	}
}
