package com.faithlog.campus.presentation;

import com.faithlog.campus.application.CampusCreateResult;
import com.faithlog.campus.application.CampusDetailResult;
import com.faithlog.campus.application.CampusMembershipResult;
import com.faithlog.campus.application.CampusService;
import com.faithlog.campus.presentation.dto.CampusCreateResponse;
import com.faithlog.campus.presentation.dto.CampusDetailResponse;
import com.faithlog.campus.presentation.dto.CampusMembershipResponse;
import com.faithlog.campus.presentation.dto.CreateCampusRequest;
import com.faithlog.campus.presentation.dto.JoinCampusRequest;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses")
public class CampusController {

	private final CampusService campusService;

	public CampusController(CampusService campusService) {
		this.campusService = campusService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<CampusCreateResponse>> createCampus(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@Valid @RequestBody CreateCampusRequest request
	) {
		CampusCreateResult result = campusService.createCampus(request.toCommand(authenticatedUser));
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(CampusCreateResponse.from(result), "캠퍼스가 생성되었습니다."));
	}

	@PostMapping("/join")
	public ResponseEntity<ApiResponse<CampusMembershipResponse>> joinCampus(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@Valid @RequestBody JoinCampusRequest request
	) {
		CampusMembershipResult result = campusService.joinCampus(request.toCommand(authenticatedUser));
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(CampusMembershipResponse.from(result), "캠퍼스 가입이 완료되었습니다."));
	}

	@GetMapping("/me")
	public ApiResponse<List<CampusMembershipResponse>> getMyCampuses(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser
	) {
		List<CampusMembershipResponse> responses = campusService.getMyCampuses(authenticatedUser.userId())
			.stream()
			.map(CampusMembershipResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}

	@GetMapping("/{campusId}")
	public ApiResponse<CampusDetailResponse> getCampus(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		CampusDetailResult result = campusService.getCampus(campusId, authenticatedUser.userId());
		return ApiResponse.success(CampusDetailResponse.from(result));
	}

	@DeleteMapping("/{campusId}/members/{membershipId}")
	public ResponseEntity<Void> deleteCampusMember(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long membershipId
	) {
		campusService.deleteCampusMember(campusId, membershipId, authenticatedUser.userId());
		return ResponseEntity.noContent().build();
	}
}
