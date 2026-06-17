package com.faithlog.user.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.faithlog.user.application.AuthService;
import com.faithlog.user.presentation.dto.LoginResponse;
import com.faithlog.user.presentation.dto.SignupResponse;
import com.faithlog.user.presentation.dto.UserMeResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AuthService authService;

	@Test
	void signup_creates_user_and_returns_api_response() throws Exception {
		when(authService.signup(any())).thenReturn(new SignupResponse(
			1L,
			"이승욱",
			"user@example.com",
			"USER",
			true
		));

		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "이승욱",
					  "email": "user@example.com",
					  "password": "1234"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
			.andExpect(jsonPath("$.data.id").value(1))
			.andExpect(jsonPath("$.data.name").value("이승욱"))
			.andExpect(jsonPath("$.data.email").value("user@example.com"))
			.andExpect(jsonPath("$.data.role").value("USER"))
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void login_returns_access_and_refresh_tokens_in_response_body() throws Exception {
		UserMeResponse user = new UserMeResponse(
			1L,
			"이승욱",
			"user@example.com",
			"USER",
			true,
			null,
			List.of()
		);
		when(authService.login(any())).thenReturn(new LoginResponse(
			user,
			"access-token",
			"refresh-token",
			1800L,
			1209600L,
			"Bearer"
		));

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "user@example.com",
					  "password": "1234"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("로그인되었습니다."))
			.andExpect(jsonPath("$.data.user.id").value(1))
			.andExpect(jsonPath("$.data.user.email").value("user@example.com"))
			.andExpect(jsonPath("$.data.user.campusMemberships").isArray())
			.andExpect(jsonPath("$.data.accessToken").value("access-token"))
			.andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
			.andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800))
			.andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(1209600))
			.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.timestamp").exists());
	}
}
