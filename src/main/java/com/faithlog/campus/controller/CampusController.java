package com.faithlog.campus.controller;

import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.result.CampusDetailResult;
import com.faithlog.campus.service.result.CampusMembershipResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.result.MyDutyAssignmentResult;
import com.faithlog.campus.controller.dto.response.CampusCreateResponse;
import com.faithlog.campus.controller.dto.response.CampusDetailResponse;
import com.faithlog.campus.controller.dto.response.CampusMembershipResponse;
import com.faithlog.campus.controller.dto.request.CreateCampusRequest;
import com.faithlog.campus.controller.dto.request.JoinCampusRequest;
import com.faithlog.campus.controller.dto.response.MyDutyAssignmentResponse;
import com.faithlog.campus.controller.dto.request.UpdateCampusRequest;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

	@GetMapping("/{campusId}/duty-assignments/me")
	public ApiResponse<MyDutyAssignmentResponse> getMyCoffeeDutyAssignment(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		MyDutyAssignmentResult result = campusService.getMyCoffeeDutyAssignment(campusId, authenticatedUser.userId());
		return ApiResponse.success(MyDutyAssignmentResponse.from(result));
	}

	@PatchMapping("/{campusId}")
	public ApiResponse<CampusDetailResponse> updateCampus(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody UpdateCampusRequest request
	) {
		CampusDetailResult result = campusService.updateCampus(request.toCommand(campusId, authenticatedUser));
		return ApiResponse.success(CampusDetailResponse.from(result), "캠퍼스가 수정되었습니다.");
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
