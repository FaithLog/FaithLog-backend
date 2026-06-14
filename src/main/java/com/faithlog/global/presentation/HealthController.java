package com.faithlog.global.presentation;

import com.faithlog.global.response.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

	@GetMapping("/health")
	public ApiResponse<Map<String, String>> health() {
		return ApiResponse.success(Map.of("status", "UP"));
	}
}
