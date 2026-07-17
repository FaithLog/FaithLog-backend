package com.faithlog.user.service;

import com.faithlog.user.service.command.LoginCommand;
import com.faithlog.user.service.command.SignupCommand;
import com.faithlog.user.service.result.CampusMembershipResult;
import com.faithlog.user.service.result.LoginResult;
import com.faithlog.user.service.result.UserMeResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private CampusService campusService;

	@Test
	void signup_hashes_password_and_rejects_duplicate_email() {
		authService.signup(new SignupCommand("이승욱", "signup@example.com", "1234"));

		User user = userRepository.findByEmail("signup@example.com").orElseThrow();
		assertThat(user.passwordHash()).isNotEqualTo("1234");
		assertThat(passwordEncoder.matches("1234", user.passwordHash())).isTrue();
		assertThat(user.role().name()).isEqualTo("USER");

		assertThatThrownBy(() -> authService.signup(new SignupCommand("다른사용자", "signup@example.com", "1234")))
			.isInstanceOf(BusinessException.class)
			.hasMessage("이미 가입된 이메일입니다.")
			.satisfies(exception -> assertThat(((BusinessException) exception).errorCode().name())
				.isEqualTo("AUTH_EMAIL_ALREADY_EXISTS"));
	}

	@Test
	void login_returns_tokens_with_required_claims_and_updates_last_login_at() {
		authService.signup(new SignupCommand("이승욱", "login@example.com", "1234"));

		LoginResult response = authService.login(new LoginCommand("login@example.com", "1234"));

		Claims accessClaims = jwtProvider.parseAccessToken(response.accessToken());
		Claims refreshClaims = jwtProvider.parseRefreshToken(response.refreshToken());
		User user = userRepository.findByEmail("login@example.com").orElseThrow();

		assertThat(response.accessTokenExpiresIn()).isEqualTo(1800L);
		assertThat(response.refreshTokenExpiresIn()).isEqualTo(1209600L);
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(accessClaims.getId()).isNotBlank();
		assertThat(accessClaims.get("userId", Long.class)).isEqualTo(user.id());
		assertThat(accessClaims.get("role", String.class)).isEqualTo("USER");
		assertThat(accessClaims.get("sessionId", String.class)).isNotBlank();
		assertThat(accessClaims.get("tokenType", String.class)).isEqualTo("ACCESS");
		assertThat(refreshClaims.get("userId", Long.class)).isEqualTo(user.id());
		assertThat(refreshClaims.get("sessionId", String.class)).isEqualTo(accessClaims.get("sessionId", String.class));
		assertThat(refreshClaims.get("refreshJti", String.class)).isNotBlank();
		assertThat(refreshClaims.get("tokenType", String.class)).isEqualTo("REFRESH");
		assertThatThrownBy(() -> jwtProvider.parseAccessToken(response.refreshToken()))
			.hasMessage("Invalid token type");
		assertThatThrownBy(() -> jwtProvider.parseRefreshToken(response.accessToken()))
			.hasMessage("Invalid token type");
		assertThat(user.lastLoginAt()).isNotNull();
	}

	@Test
	void login_and_current_user_include_active_campus_memberships() {
		authService.signup(new SignupCommand("매니저", "login-membership-manager@example.com", "1234"));
		authService.signup(new SignupCommand("멤버", "login-membership-member@example.com", "1234"));
		User manager = userRepository.findByEmail("login-membership-manager@example.com").orElseThrow();
		User member = userRepository.findByEmail("login-membership-member@example.com").orElseThrow();
		ReflectionTestUtils.setField(manager, "role", UserRole.MANAGER);
		userRepository.saveAndFlush(manager);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"로그인멤버십캠",
			"분당",
			"로그인 응답 멤버십 테스트"
		));
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));

		LoginResult login = authService.login(new LoginCommand("login-membership-member@example.com", "1234"));
		UserMeResult me = authService.getCurrentUser(member.id());

		assertThat(login.user().campusMemberships())
			.extracting(CampusMembershipResult::campusId, CampusMembershipResult::campusRole, CampusMembershipResult::status)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(campus.campusId(), "MEMBER", "ACTIVE"));
		assertThat(me.campusMemberships())
			.extracting(CampusMembershipResult::campusId, CampusMembershipResult::campusRole, CampusMembershipResult::status)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(campus.campusId(), "MEMBER", "ACTIVE"));
	}
}
