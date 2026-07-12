package com.faithlog.user.service;

import com.faithlog.user.service.command.LoginCommand;
import com.faithlog.user.service.command.LogoutCommand;
import com.faithlog.user.service.command.RefreshCommand;
import com.faithlog.user.service.command.SignupCommand;
import com.faithlog.user.service.result.LoginResult;
import com.faithlog.user.service.result.SignupResult;
import com.faithlog.user.service.result.TokenResult;
import com.faithlog.user.service.result.UserMeResult;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private final SignupCommandService signupCommandService;
	private final LoginCommandService loginCommandService;
	private final RefreshTokenRotationService refreshTokenRotationService;
	private final LogoutCommandService logoutCommandService;
	private final UserMeQueryService userMeQueryService;

	public AuthService(
		SignupCommandService signupCommandService,
		LoginCommandService loginCommandService,
		RefreshTokenRotationService refreshTokenRotationService,
		LogoutCommandService logoutCommandService,
		UserMeQueryService userMeQueryService
	) {
		this.signupCommandService = signupCommandService;
		this.loginCommandService = loginCommandService;
		this.refreshTokenRotationService = refreshTokenRotationService;
		this.logoutCommandService = logoutCommandService;
		this.userMeQueryService = userMeQueryService;
	}

	public SignupResult signup(SignupCommand command) {
		return signupCommandService.signup(command);
	}

	public LoginResult login(LoginCommand command) {
		return loginCommandService.login(command);
	}

	public TokenResult refresh(RefreshCommand command) {
		return refreshTokenRotationService.refresh(command);
	}

	public void logout(LogoutCommand command) {
		logoutCommandService.logout(command);
	}

	public UserMeResult getCurrentUser(Long userId) {
		return userMeQueryService.getCurrentUser(userId);
	}
}
