package com.faithlog.companion;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/companions/me/garden")
public class GardenController {
 private final GardenService service;
 public GardenController(GardenService service){this.service=service;}
 @GetMapping public ApiResponse<GardenService.Garden> get(@AuthenticationPrincipal AuthenticatedUser user){return ApiResponse.success(service.get(user.userId()));}
 @PutMapping public ApiResponse<GardenService.Garden> save(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody GardenUpdate request){return ApiResponse.success(service.save(user.userId(),request));}
}
