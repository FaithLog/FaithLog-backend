package com.faithlog.global.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthenticationFailureObservabilityTest {

	private final OperationalEventPort events = mock(OperationalEventPort.class);
	private final GlobalExceptionHandler handler = new GlobalExceptionHandler(events);

	@Test
	void records_login_failure_with_bounded_flow_and_error_code() {
		MockHttpServletRequest request = post("/api/v1/auth/login");

		var response = handler.handleBusinessException(
			new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		verify(events).authenticationFailure(AuthFlow.LOGIN, AuthFailure.INVALID_CREDENTIALS);
	}

	@Test
	void records_refresh_failure_without_token_or_user_labels() {
		MockHttpServletRequest request = post("/api/v1/auth/refresh");

		handler.handleBusinessException(new BusinessException(ErrorCode.AUTH_UNAUTHORIZED), request);

		verify(events).authenticationFailure(AuthFlow.REFRESH_TOKEN, AuthFailure.UNAUTHORIZED);
	}

	@Test
	void records_email_verification_failures_as_one_bounded_family() {
		MockHttpServletRequest request = post("/api/v1/auth/email-verifications/signup/confirm");

		handler.handleBusinessException(
			new BusinessException(ErrorCode.AUTH_EMAIL_VERIFICATION_CODE_INVALID), request);

		verify(events).authenticationFailure(AuthFlow.EMAIL_VERIFICATION, AuthFailure.INVALID_CODE);
	}

	private MockHttpServletRequest post(String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
		request.setRequestURI(uri);
		return request;
	}
}
