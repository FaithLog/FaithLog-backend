package com.faithlog.companion;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/friend-gardens")
public class FriendGardenController {
    private final FriendGardenService service;

    public FriendGardenController(FriendGardenService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<FriendGardenService.Page> list(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long campusId, @RequestParam(defaultValue = "0") long after) {
        return ApiResponse.success(service.list(campusId, user.userId(), after));
    }

    @PutMapping("/me/visibility")
    public ApiResponse<FriendGardenService.Visibility> setVisibility(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long campusId, @Valid @RequestBody VisibilityRequest request) {
        return ApiResponse.success(service.visibility(campusId, user.userId(), request.visible()));
    }

    @GetMapping("/weekly-goal")
    public ApiResponse<FriendGardenService.WeeklyGoal> weeklyGoal(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long campusId) {
        return ApiResponse.success(service.weeklyGoal(campusId, user.userId()));
    }

    @GetMapping("/{ownerId}")
    public ApiResponse<FriendGardenService.Visit> visit(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long campusId, @PathVariable long ownerId) {
        return ApiResponse.success(service.visit(campusId, user.userId(), ownerId));
    }

    @PostMapping("/{ownerId}/help")
    public ApiResponse<FriendGardenService.HelpResult> help(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long campusId, @PathVariable long ownerId, @RequestBody HelpRequest request) {
        return ApiResponse.success(service.help(campusId, user.userId(), ownerId, request.slot(), request.action()));
    }

    public record HelpRequest(int slot, String action) {}
    public record VisibilityRequest(@NotNull Boolean visible) {}
}
