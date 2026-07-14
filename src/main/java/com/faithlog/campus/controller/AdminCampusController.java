package com.faithlog.campus.controller;

import com.faithlog.campus.service.result.AdminCampusMemberResult;
import com.faithlog.campus.service.CampusDutyAssignmentService;
import com.faithlog.campus.service.CampusMemberManagementService;
import com.faithlog.campus.service.result.DutyAssignmentResult;
import com.faithlog.campus.controller.dto.request.AssignCoffeeDutyRequest;
import com.faithlog.campus.controller.dto.request.AssignMealDutyRequest;
import com.faithlog.campus.controller.dto.response.CampusMemberAdminResponse;
import com.faithlog.campus.controller.dto.request.ChangeCampusRoleRequest;
import com.faithlog.campus.controller.dto.response.DutyAssignmentResponse;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campuses")
public class AdminCampusController {

	private final CampusMemberManagementService campusMemberManagementService;
	private final CampusDutyAssignmentService campusDutyAssignmentService;

	public AdminCampusController(
		CampusMemberManagementService campusMemberManagementService,
		CampusDutyAssignmentService campusDutyAssignmentService
	) {
		this.campusMemberManagementService = campusMemberManagementService;
		this.campusDutyAssignmentService = campusDutyAssignmentService;
	}

	@GetMapping("/{campusId}/members")
	public ApiResponse<List<CampusMemberAdminResponse>> getCampusMembers(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		List<CampusMemberAdminResponse> responses = campusMemberManagementService
			.getCampusMembers(campusId, authenticatedUser.userId())
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
		AdminCampusMemberResult result = campusMemberManagementService.changeCampusRole(request.toCommand(
			campusId,
			campusMemberId,
			authenticatedUser
		));
		return ApiResponse.success(CampusMemberAdminResponse.from(result));
	}

	@GetMapping("/{campusId}/duty-assignments")
	public ApiResponse<List<DutyAssignmentResponse>> getDutyAssignments(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam(defaultValue = "false") boolean staleOnly
	) {
		List<DutyAssignmentResponse> responses = campusDutyAssignmentService
			.getDutyAssignments(campusId, authenticatedUser.userId(), staleOnly)
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
		DutyAssignmentResult result = campusDutyAssignmentService.assignCoffeeDuty(
			request.toCommand(campusId, authenticatedUser)
		);
		return ApiResponse.success(DutyAssignmentResponse.from(result));
	}

	@PostMapping("/{campusId}/duty-assignments/meal")
	public ApiResponse<DutyAssignmentResponse> assignMealDuty(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody AssignMealDutyRequest request
	) {
		DutyAssignmentResult result = campusDutyAssignmentService.assignMealDuty(
			request.toCommand(campusId, authenticatedUser)
		);
		return ApiResponse.success(DutyAssignmentResponse.from(result));
	}

	@DeleteMapping("/{campusId}/duty-assignments/coffee/{assignmentId}")
	public ResponseEntity<Void> revokeCoffeeDuty(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long assignmentId
	) {
		campusDutyAssignmentService.revokeCoffeeDuty(campusId, assignmentId, authenticatedUser.userId());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{campusId}/duty-assignments/meal/{assignmentId}")
	public ResponseEntity<Void> revokeMealDuty(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long assignmentId
	) {
		campusDutyAssignmentService.revokeMealDuty(campusId, assignmentId, authenticatedUser.userId());
		return ResponseEntity.noContent().build();
	}
}
