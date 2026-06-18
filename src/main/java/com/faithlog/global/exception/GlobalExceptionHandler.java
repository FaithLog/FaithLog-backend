package com.faithlog.global.exception;

import com.faithlog.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.errorCode();
		return ResponseEntity
			.status(errorCode.status())
			.body(ApiResponse.failure(errorCode.name(), exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.findFirst()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.orElse(ErrorCode.INVALID_REQUEST.message());

		return ResponseEntity
			.badRequest()
			.body(ApiResponse.failure(ErrorCode.INVALID_REQUEST.name(), message));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception) {
		return ResponseEntity
			.badRequest()
			.body(ApiResponse.failure(ErrorCode.INVALID_REQUEST.name(), ErrorCode.INVALID_REQUEST.message()));
	}
}
