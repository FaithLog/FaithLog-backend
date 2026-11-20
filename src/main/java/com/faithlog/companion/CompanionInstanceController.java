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
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="50") int size) {
        return ApiResponse.success(service.list(user.userId(),page,size));
    }

    /** Only queried from the collection page when a specific design is selected. */
    @GetMapping("/by-design")
    public ApiResponse<List<CompanionInstanceService.View>> byDesign(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String designId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(service.listByDesign(
                user.userId(),designId,page,size));
    }

    /** Read at most the five visible companions for the home garden. */
    @GetMapping("/garden")
    public ApiResponse<List<CompanionInstanceService.View>> garden(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.gardenInstances(user.userId()));
    }

    @PostMapping("/visit")
    public ApiResponse<java.util.Map<String,Boolean>> visit(@AuthenticationPrincipal AuthenticatedUser user) {
        service.recordVisit(user.userId());
        return ApiResponse.success(java.util.Map.of("recorded",true));
    }

    @PostMapping("/{id}/wake")
    public ApiResponse<CompanionInstanceService.View> wake(
            @AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id) {
        return ApiResponse.success(service.wake(user.userId(),id));
    }

    @PostMapping("/{id}/care")
    public ApiResponse<CompanionInstanceService.View> care(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody CompanionInstanceService.CareRequest request) {
        return ApiResponse.success(service.care(user.userId(),id,key,request));
    }

    @PostMapping("/{id}/growth-shard-boost")
    public ApiResponse<CompanionInstanceService.View> boostWithShards(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody CompanionInstanceService.EvolveRequest request) {
        return ApiResponse.success(service.boostWithShards(user.userId(),id,key,request));
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
