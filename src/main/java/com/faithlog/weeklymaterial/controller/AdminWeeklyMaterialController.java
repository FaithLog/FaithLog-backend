package com.faithlog.weeklymaterial.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.weeklymaterial.controller.dto.request.PutWeeklyMaterialRequest;
import com.faithlog.weeklymaterial.controller.dto.response.WeeklyMaterialWeekResponse;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.WeeklyMaterialCommandService;
import com.faithlog.weeklymaterial.service.WeeklyMaterialQueryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/campuses/{campusId}/weekly-materials")
public class AdminWeeklyMaterialController {
	private final WeeklyMaterialCommandService commands;
	private final WeeklyMaterialQueryService queries;

	public AdminWeeklyMaterialController(WeeklyMaterialCommandService commands, WeeklyMaterialQueryService queries) {
		this.commands = commands;
		this.queries = queries;
	}

	@PutMapping("/{weekStartDate}/{materialType}")
	public ResponseEntity<ApiResponse<WeeklyMaterialWeekResponse>> put(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long campusId,
		@PathVariable LocalDate weekStartDate, @PathVariable WeeklyMaterialType materialType,
		@Valid @RequestBody PutWeeklyMaterialRequest request) {
		commands.put(campusId, weekStartDate, materialType, request.mediaAssetId(), user.userId());
		return ResponseEntity.ok(ApiResponse.success(WeeklyMaterialWeekResponse.from(
			queries.getWeek(campusId, user.userId(), weekStartDate))));
	}

	@DeleteMapping("/{weekStartDate}/{materialType}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long campusId, @PathVariable LocalDate weekStartDate,
		@PathVariable WeeklyMaterialType materialType) {
		commands.delete(campusId, weekStartDate, materialType, user.userId());
		return ResponseEntity.noContent().build();
	}
}
