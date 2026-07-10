package com.faithlog.admin.controller;

import com.faithlog.admin.service.query.AdminCampusSearchCriteria;
import com.faithlog.admin.service.query.AdminCampusStatus;
import com.faithlog.admin.service.AdminManagementService;
import com.faithlog.admin.service.query.AdminUserSearchCriteria;
import com.faithlog.admin.controller.dto.request.AddCampusMemberRequest;
import com.faithlog.admin.controller.dto.response.AdminCampusResponse;
import com.faithlog.admin.controller.dto.response.AdminUserCampusResponse;
import com.faithlog.admin.controller.dto.response.AdminUserDetailResponse;
import com.faithlog.admin.controller.dto.response.AdminUserSummaryResponse;
import com.faithlog.admin.controller.dto.request.ChangeUserRoleRequest;
import com.faithlog.campus.controller.dto.response.CampusMemberAdminResponse;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.response.PageResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.user.domain.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminManagementController {

	private final AdminManagementService adminManagementService;

	public AdminManagementController(AdminManagementService adminManagementService) {
		this.adminManagementService = adminManagementService;
	}

	@GetMapping("/users")
	public ApiResponse<PageResponse<AdminUserSummaryResponse>> searchUsers(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestParam(required = false) String name,
		@RequestParam(required = false) String email,
		@RequestParam(required = false) Long userId,
		@RequestParam(required = false) UserRole role,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return ApiResponse.success(PageResponse.from(
			adminManagementService.searchUsers(
				authenticatedUser.userId(),
				new AdminUserSearchCriteria(name, email, userId, role),
				AdminPageRequests.users(page, size, sort)
			).map(AdminUserSummaryResponse::from)
		));
	}

	@GetMapping("/users/{userId}")
	public ApiResponse<AdminUserDetailResponse> getUser(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long userId
	) {
		return ApiResponse.success(AdminUserDetailResponse.from(
			adminManagementService.getUser(authenticatedUser.userId(), userId)
		));
	}

	@PatchMapping("/users/{userId}/role")
	public ApiResponse<AdminUserDetailResponse> changeUserRole(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long userId,
		@Valid @RequestBody ChangeUserRoleRequest request
	) {
		return ApiResponse.success(AdminUserDetailResponse.from(
			adminManagementService.changeUserRole(request.toCommand(userId, authenticatedUser))
		), "사용자 역할이 변경되었습니다.");
	}

	@GetMapping("/campuses")
	public ApiResponse<PageResponse<AdminCampusResponse>> searchCampuses(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestParam(required = false) String name,
		@RequestParam(required = false) String region,
		@RequestParam(required = false) AdminCampusStatus status,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return ApiResponse.success(PageResponse.from(
			adminManagementService.searchCampuses(
				authenticatedUser.userId(),
				new AdminCampusSearchCriteria(name, region, status),
				AdminPageRequests.campuses(page, size, sort)
			).map(AdminCampusResponse::from)
		));
	}

	@PostMapping("/campuses/{campusId}/members")
	public ResponseEntity<ApiResponse<CampusMemberAdminResponse>> addCampusMember(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody AddCampusMemberRequest request
	) {
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(CampusMemberAdminResponse.from(
				adminManagementService.addCampusMember(request.toCommand(campusId, authenticatedUser))
			), "캠퍼스 멤버가 추가되었습니다."));
	}
}
