package com.faithlog.notification.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.notification.service.FcmTokenCommandService;
import com.faithlog.notification.controller.dto.response.FcmTokenResponse;
import com.faithlog.notification.controller.dto.request.RegisterFcmTokenRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/fcm-tokens")
public class FcmTokenController {

	private final FcmTokenCommandService fcmTokenCommandService;

	public FcmTokenController(FcmTokenCommandService fcmTokenCommandService) {
		this.fcmTokenCommandService = fcmTokenCommandService;
	}

	@PostMapping
	public ApiResponse<FcmTokenResponse> registerToken(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@Valid @RequestBody RegisterFcmTokenRequest request
	) {
		return ApiResponse.success(FcmTokenResponse.from(fcmTokenCommandService.registerToken(request.toCommand(authenticatedUser.userId()))));
	}

	@DeleteMapping("/{tokenId}")
	public ResponseEntity<Void> deactivateToken(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long tokenId
	) {
		fcmTokenCommandService.deactivateToken(authenticatedUser.userId(), tokenId);
		return ResponseEntity.noContent().build();
	}
}
