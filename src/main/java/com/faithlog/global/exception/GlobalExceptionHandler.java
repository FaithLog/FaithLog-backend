package com.faithlog.global.exception;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.observability.AuthFailure;
import com.faithlog.global.observability.AuthFlow;
import com.faithlog.global.observability.OperationalEventPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private OperationalEventPort operationalEvents = OperationalEventPort.noop();

	public GlobalExceptionHandler() {
	}

	public GlobalExceptionHandler(OperationalEventPort operationalEvents) {
		this.operationalEvents = operationalEvents;
	}

	@Autowired(required = false)
	void setOperationalEvents(OperationalEventPort operationalEvents) {
		this.operationalEvents = operationalEvents;
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(
		BusinessException exception,
		HttpServletRequest request
	) {
		ErrorCode errorCode = exception.errorCode();
		recordAuthenticationFailure(request.getRequestURI(), errorCode);
		return ResponseEntity
			.status(errorCode.status())
			.body(ApiResponse.failure(errorCode.name(), exception.getMessage()));
	}

	private void recordAuthenticationFailure(String requestUri, ErrorCode errorCode) {
		if ("/api/v1/auth/login".equals(requestUri) && errorCode == ErrorCode.AUTH_INVALID_CREDENTIALS) {
			operationalEvents.authenticationFailure(AuthFlow.LOGIN, AuthFailure.INVALID_CREDENTIALS);
			return;
		}
		if ("/api/v1/auth/refresh".equals(requestUri) && errorCode == ErrorCode.AUTH_UNAUTHORIZED) {
			operationalEvents.authenticationFailure(AuthFlow.REFRESH_TOKEN, AuthFailure.UNAUTHORIZED);
			return;
		}
		if (requestUri.startsWith("/api/v1/auth/email-verifications/")
			|| requestUri.startsWith("/api/v1/auth/password-resets/")) {
			AuthFailure failure = switch (errorCode) {
				case AUTH_EMAIL_VERIFICATION_CODE_INVALID -> AuthFailure.INVALID_CODE;
				case AUTH_EMAIL_VERIFICATION_CODE_EXPIRED -> AuthFailure.EXPIRED_CODE;
				case AUTH_EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED -> AuthFailure.ATTEMPTS_EXCEEDED;
				case AUTH_EMAIL_VERIFICATION_RESEND_THROTTLED,
					AUTH_EMAIL_VERIFICATION_RATE_LIMITED -> AuthFailure.RATE_LIMITED;
				case AUTH_EMAIL_VERIFICATION_TOKEN_INVALID,
					AUTH_PASSWORD_RESET_TOKEN_INVALID -> AuthFailure.INVALID_TOKEN;
				case AUTH_EMAIL_DELIVERY_UNAVAILABLE,
					AUTH_EMAIL_VERIFICATION_UNAVAILABLE -> AuthFailure.SERVICE_UNAVAILABLE;
				default -> null;
			};
			if (failure != null) {
				operationalEvents.authenticationFailure(AuthFlow.EMAIL_VERIFICATION, failure);
			}
		}
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.findFirst()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.orElse(ErrorCode.GLOBAL_VALIDATION_FAILED.message());

		return ResponseEntity
			.badRequest()
			.body(ApiResponse.failure(ErrorCode.GLOBAL_VALIDATION_FAILED.name(), message));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception) {
		return ResponseEntity
			.badRequest()
			.body(ApiResponse.failure(ErrorCode.GLOBAL_INVALID_JSON.name(), ErrorCode.GLOBAL_INVALID_JSON.message()));
	}

}
