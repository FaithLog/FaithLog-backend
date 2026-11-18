package com.faithlog.companion;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companions/me/instances")
public class CompanionInstanceController {
    private final CompanionInstanceService service;

    public CompanionInstanceController(CompanionInstanceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<CompanionInstanceService.View>> list(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.list(user.userId()));
    }

    @PostMapping("/{id}/care")
    public ApiResponse<CompanionInstanceService.View> care(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody CompanionInstanceService.CareRequest request) {
        return ApiResponse.success(service.care(user.userId(),id,key,request));
    }

    @PostMapping("/{id}/evolve")
    public ApiResponse<CompanionInstanceService.View> evolve(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody CompanionInstanceService.EvolveRequest request) {
        return ApiResponse.success(service.evolve(user.userId(),id,key,request));
    }
}
