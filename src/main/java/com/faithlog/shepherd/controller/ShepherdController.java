package com.faithlog.shepherd.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.shepherd.controller.dto.request.CreateShepherdGroupRequest;
import com.faithlog.shepherd.controller.dto.request.SaveShepherdAttendanceRequest;
import com.faithlog.shepherd.controller.dto.response.ShepherdAttendanceReportResponse;
import com.faithlog.shepherd.controller.dto.response.ShepherdGroupResponse;
import com.faithlog.shepherd.service.ShepherdService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/shepherd-groups")
public class ShepherdController {

	private final ShepherdService shepherdService;

	public ShepherdController(ShepherdService shepherdService) {
		this.shepherdService = shepherdService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ShepherdGroupResponse>> createGroup(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreateShepherdGroupRequest request
	) {
		ShepherdGroupResponse response = ShepherdGroupResponse.from(
			shepherdService.createGroup(request.toCommand(campusId, authenticatedUser)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@GetMapping("/me")
	public ApiResponse<List<ShepherdGroupResponse>> getMyGroups(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		return ApiResponse.success(shepherdService.getMyGroups(campusId, authenticatedUser.userId())
			.stream()
			.map(ShepherdGroupResponse::from)
			.toList());
	}

	@GetMapping("/{groupId}/attendance/{serviceDate}")
	public ApiResponse<ShepherdAttendanceReportResponse> getAttendance(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@PathVariable Long groupId,
		@PathVariable LocalDate serviceDate
	) {
		var result = shepherdService.getAttendance(campusId, groupId, serviceDate, authenticatedUser.userId());
		return ApiResponse.success(result == null ? null : ShepherdAttendanceReportResponse.from(result));
	}

	@PutMapping("/{groupId}/attendance/{serviceDate}")
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
