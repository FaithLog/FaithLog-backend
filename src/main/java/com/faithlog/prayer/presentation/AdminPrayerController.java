package com.faithlog.prayer.presentation;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.prayer.application.PrayerService;
import com.faithlog.prayer.presentation.dto.ClosePrayerSeasonRequest;
import com.faithlog.prayer.presentation.dto.CreatePrayerGroupRequest;
import com.faithlog.prayer.presentation.dto.CreatePrayerSeasonRequest;
import com.faithlog.prayer.presentation.dto.PrayerGroupResponse;
import com.faithlog.prayer.presentation.dto.PrayerSeasonResponse;
import com.faithlog.prayer.presentation.dto.ReplacePrayerGroupMembersRequest;
import com.faithlog.prayer.presentation.dto.UpdatePrayerGroupRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

	private final PrayerService prayerService;

	public AdminPrayerController(PrayerService prayerService) {
		this.prayerService = prayerService;
	}

	@PostMapping("/campuses/{campusId}/prayer-seasons")
	public ResponseEntity<ApiResponse<PrayerSeasonResponse>> createSeason(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long campusId,
		@Valid @RequestBody CreatePrayerSeasonRequest request
	) {
		PrayerSeasonResponse response = PrayerSeasonResponse.from(prayerService.createSeason(request.toCommand(campusId, authenticatedUser)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@PatchMapping("/prayer-seasons/{seasonId}/close")
	public ApiResponse<PrayerSeasonResponse> closeSeason(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long seasonId,
		@Valid @RequestBody ClosePrayerSeasonRequest request
	) {
		return ApiResponse.success(PrayerSeasonResponse.from(prayerService.closeSeason(request.toCommand(seasonId, authenticatedUser))));
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
