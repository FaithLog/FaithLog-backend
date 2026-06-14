package com.faithlog.global.response;

import java.time.Instant;

public record ApiResponse<T>(
	boolean success,
	String code,
	String message,
	T data,
	Instant timestamp
) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, "SUCCESS", "요청이 성공했습니다.", data, Instant.now());
	}

	public static ApiResponse<Void> ok() {
		return success(null);
	}

	public static ApiResponse<Void> failure(String code, String message) {
		return new ApiResponse<>(false, code, message, null, Instant.now());
	}
}
