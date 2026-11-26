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

    @GetMapping("/{ownerId}")
    public ApiResponse<FriendGardenService.Visit> visit(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long campusId, @PathVariable long ownerId) {
        return ApiResponse.success(service.visit(campusId, user.userId(), ownerId));
    }

    public record VisibilityRequest(@NotNull Boolean visible) {}
}
