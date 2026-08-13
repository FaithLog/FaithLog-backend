package com.faithlog.shepherd.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.shepherd.controller.dto.request.ReplaceShepherdGroupAssigneesRequest;
import com.faithlog.shepherd.controller.dto.request.SaveShepherdAttendanceRequest;
import com.faithlog.shepherd.controller.dto.request.UpdateShepherdGroupRequest;
import com.faithlog.shepherd.controller.dto.response.ShepherdAttendanceBoardResponse;
import com.faithlog.shepherd.controller.dto.response.ShepherdAttendanceReportResponse;
import com.faithlog.shepherd.controller.dto.response.ShepherdGroupResponse;
import com.faithlog.shepherd.service.ShepherdService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campuses/{campusId}")
public class AdminShepherdController {

	private final ShepherdService shepherdService;

	public AdminShepherdController(ShepherdService shepherdService) {
		this.shepherdService = shepherdService;
	}

	@GetMapping("/shepherd-groups")
	public ApiResponse<List<ShepherdGroupResponse>> getGroups(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		return ApiResponse.success(shepherdService.getAdminGroups(campusId, authenticatedUser.userId())
			.stream()
			.map(ShepherdGroupResponse::from)
			.toList());
	}

	@PatchMapping("/shepherd-groups/{groupId}")
	public ApiResponse<ShepherdGroupResponse> updateGroup(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long groupId,
		@Valid @RequestBody UpdateShepherdGroupRequest request
	) {
		return ApiResponse.success(ShepherdGroupResponse.from(
			shepherdService.updateGroup(request.toCommand(campusId, groupId, authenticatedUser))));
	}

	@PutMapping("/shepherd-groups/{groupId}/assignees")
	public ApiResponse<ShepherdGroupResponse> replaceAssignees(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long groupId,
		@Valid @RequestBody ReplaceShepherdGroupAssigneesRequest request
	) {
		return ApiResponse.success(ShepherdGroupResponse.from(
			shepherdService.replaceAssignees(request.toCommand(campusId, groupId, authenticatedUser))));
	}

	@GetMapping("/shepherd-attendance")
	public ApiResponse<ShepherdAttendanceBoardResponse> getAttendanceBoard(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@RequestParam LocalDate serviceDate,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "50") int size
	) {
		return ApiResponse.success(ShepherdAttendanceBoardResponse.from(
			shepherdService.getAdminAttendanceBoard(
				campusId, serviceDate, authenticatedUser.userId(), page, size)));
	}

	@PutMapping("/shepherd-groups/{groupId}/attendance/{serviceDate}")
	public ApiResponse<ShepherdAttendanceReportResponse> saveAttendance(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long groupId,
		@PathVariable LocalDate serviceDate,
		@Valid @RequestBody SaveShepherdAttendanceRequest request
	) {
		return ApiResponse.success(ShepherdAttendanceReportResponse.from(
			shepherdService.saveAttendance(request.toCommand(campusId, groupId, serviceDate, authenticatedUser))));
	}
}
