package com.faithlog.user.service.result;

import com.faithlog.global.security.JwtProvider.IssuedTokens;

public record LoginResult(
	UserMeResult user,
	String accessToken,
	String refreshToken,
	long accessTokenExpiresIn,
	long refreshTokenExpiresIn,
	String tokenType
) {

	public static LoginResult of(UserMeResult user, IssuedTokens tokens) {
		return new LoginResult(
			user,
			tokens.accessToken(),
			tokens.refreshToken(),
			tokens.accessTokenExpiresIn(),
			tokens.refreshTokenExpiresIn(),
			"Bearer"
		);
	}
}
