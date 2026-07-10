package com.faithlog.prayer.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.service.PrayerService;
import com.faithlog.prayer.service.PrayerSeasonCommandService;
import com.faithlog.prayer.service.PrayerSeasonQueryService;
import com.faithlog.prayer.controller.dto.request.ClosePrayerSeasonRequest;
import com.faithlog.prayer.controller.dto.request.CreatePrayerGroupRequest;
import com.faithlog.prayer.controller.dto.request.CreatePrayerSeasonRequest;
import com.faithlog.prayer.controller.dto.response.PrayerAssignableMemberResponse;
import com.faithlog.prayer.controller.dto.response.PrayerGroupResponse;
import com.faithlog.prayer.controller.dto.response.PrayerSeasonResponse;
import com.faithlog.prayer.controller.dto.request.ReplacePrayerGroupMembersRequest;
import com.faithlog.prayer.controller.dto.request.UpdatePrayerGroupRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPrayerController {

	private final PrayerSeasonCommandService seasonCommandService;
	private final PrayerSeasonQueryService seasonQueryService;
	private final PrayerService prayerService;

	public AdminPrayerController(
		PrayerSeasonCommandService seasonCommandService,
		PrayerSeasonQueryService seasonQueryService,
		PrayerService prayerService
	) {
		this.seasonCommandService = seasonCommandService;
		this.seasonQueryService = seasonQueryService;
		this.prayerService = prayerService;
	}

	@PostMapping("/campuses/{campusId}/prayer-seasons")
	public ResponseEntity<ApiResponse<PrayerSeasonResponse>> createSeason(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreatePrayerSeasonRequest request
	) {
		PrayerSeasonResponse response = PrayerSeasonResponse.from(seasonCommandService.createSeason(request.toCommand(campusId, authenticatedUser)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@GetMapping("/campuses/{campusId}/prayer-seasons/current")
	public ApiResponse<PrayerSeasonResponse> getCurrentSeason(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId
	) {
		var result = seasonQueryService.getCurrentSeason(campusId, authenticatedUser.userId());
		return ApiResponse.success(result == null ? null : PrayerSeasonResponse.from(result));
	}

	@PatchMapping("/prayer-seasons/{seasonId}/close")
	public ApiResponse<PrayerSeasonResponse> closeSeason(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long seasonId,
		@Valid @RequestBody ClosePrayerSeasonRequest request
	) {
		return ApiResponse.success(PrayerSeasonResponse.from(seasonCommandService.closeSeason(request.toCommand(seasonId, authenticatedUser))));
	}

	@PostMapping("/prayer-seasons/{seasonId}/groups")
	public ResponseEntity<ApiResponse<PrayerGroupResponse>> createGroup(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long seasonId,
		@Valid @RequestBody CreatePrayerGroupRequest request
	) {
		PrayerGroupResponse response = PrayerGroupResponse.from(prayerService.createGroup(request.toCommand(seasonId, authenticatedUser)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@GetMapping("/prayer-seasons/{seasonId}/groups")
	public ApiResponse<List<PrayerGroupResponse>> getSeasonGroups(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long seasonId
	) {
		return ApiResponse.success(prayerService.getSeasonGroups(seasonId, authenticatedUser.userId())
			.stream()
			.map(PrayerGroupResponse::from)
			.toList());
	}

	@GetMapping("/prayer-seasons/{seasonId}/members/assignable")
	public ApiResponse<List<PrayerAssignableMemberResponse>> getAssignableMembers(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long seasonId
	) {
		return ApiResponse.success(prayerService.getAssignableMembers(seasonId, authenticatedUser.userId())
			.stream()
			.map(PrayerAssignableMemberResponse::from)
			.toList());
	}

	@PatchMapping("/prayer-groups/{groupId}")
	public ApiResponse<PrayerGroupResponse> updateGroup(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long groupId,
		@Valid @RequestBody UpdatePrayerGroupRequest request
	) {
		return ApiResponse.success(PrayerGroupResponse.from(prayerService.updateGroup(request.toCommand(groupId, authenticatedUser))));
	}

	@PutMapping("/prayer-groups/{groupId}/members")
	public ApiResponse<PrayerGroupResponse> replaceGroupMembers(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long groupId,
		@Valid @RequestBody ReplacePrayerGroupMembersRequest request
	) {
		return ApiResponse.success(PrayerGroupResponse.from(prayerService.replaceGroupMembers(request.toCommand(groupId, authenticatedUser))));
	}
}
