package com.faithlog.campus.presentation;

import com.faithlog.campus.application.AdminCampusMemberResult;
import com.faithlog.campus.application.CampusService;
import com.faithlog.campus.application.DutyAssignmentResult;
import com.faithlog.campus.presentation.dto.AssignCoffeeDutyRequest;
import com.faithlog.campus.presentation.dto.CampusMemberAdminResponse;
import com.faithlog.campus.presentation.dto.ChangeCampusRoleRequest;
import com.faithlog.campus.presentation.dto.DutyAssignmentResponse;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campuses")
public class AdminCampusController {

	private final CampusService campusService;

	public AdminCampusController(CampusService campusService) {
		this.campusService = campusService;
	}

	@GetMapping("/{campusId}/members")
	public ApiResponse<List<CampusMemberAdminResponse>> getCampusMembers(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		List<CampusMemberAdminResponse> responses = campusService.getCampusMembers(campusId, authenticatedUser.userId())
			.stream()
			.map(CampusMemberAdminResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}

	@PatchMapping("/{campusId}/members/{campusMemberId}/campus-role")
	public ApiResponse<CampusMemberAdminResponse> changeCampusRole(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long campusMemberId,
		@Valid @RequestBody ChangeCampusRoleRequest request
	) {
		AdminCampusMemberResult result = campusService.changeCampusRole(request.toCommand(
			campusId,
			campusMemberId,
			authenticatedUser
		));
		return ApiResponse.success(CampusMemberAdminResponse.from(result));
	}

	@GetMapping("/{campusId}/duty-assignments")
	public ApiResponse<List<DutyAssignmentResponse>> getDutyAssignments(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		List<DutyAssignmentResponse> responses = campusService.getDutyAssignments(campusId, authenticatedUser.userId())
			.stream()
			.map(DutyAssignmentResponse::from)
			.toList();
		return ApiResponse.success(responses);
	}

	@PutMapping("/{campusId}/duty-assignments/coffee")
	public ApiResponse<DutyAssignmentResponse> assignCoffeeDuty(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody AssignCoffeeDutyRequest request
	) {
		DutyAssignmentResult result = campusService.assignCoffeeDuty(request.toCommand(campusId, authenticatedUser));
		return ApiResponse.success(DutyAssignmentResponse.from(result));
	}

	@DeleteMapping("/{campusId}/duty-assignments/coffee/{assignmentId}")
	public ResponseEntity<Void> revokeCoffeeDuty(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long assignmentId
	) {
		campusService.revokeCoffeeDuty(campusId, assignmentId, authenticatedUser.userId());
		return ResponseEntity.noContent().build();
	}
}
