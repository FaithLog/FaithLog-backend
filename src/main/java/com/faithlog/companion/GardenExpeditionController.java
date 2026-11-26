package com.faithlog.companion;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companions/me/garden/expedition")
public class GardenExpeditionController {
    private final GardenExpeditionService service;

    public GardenExpeditionController(GardenExpeditionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<GardenExpeditionService.Status> status(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.status(user.userId()));
    }

    @PostMapping
    public ApiResponse<GardenExpeditionService.Status> start(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StartRequest request) {
        return ApiResponse.success(service.start(user.userId(), request.instanceId()));
    }

    @PostMapping("/{id}/claim")
    public ApiResponse<GardenExpeditionService.Status> claim(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        return ApiResponse.success(service.claim(user.userId(), id));
    }

    public record StartRequest(@NotNull UUID instanceId) {}
}
