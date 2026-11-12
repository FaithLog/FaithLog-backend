package com.faithlog.companion;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/companions/me")
public class CompanionEconomyController {
 private final CompanionEconomyService service;
 public CompanionEconomyController(CompanionEconomyService service){this.service=service;}
 @GetMapping("/draws") public ApiResponse<java.util.List<CompanionEconomyService.HistoryItem>> history(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="0") int page){return ApiResponse.success(service.drawHistory(user.userId(),page));}
 @PostMapping("/draws") public ApiResponse<CompanionEconomyService.Result> draw(@AuthenticationPrincipal AuthenticatedUser user,@RequestHeader("Idempotency-Key") String key,@RequestBody CompanionEconomyService.DrawRequest request){return ApiResponse.success(service.draw(user.userId(),key,request));}
 @PostMapping("/sales") public ApiResponse<CompanionEconomyService.Result> sell(@AuthenticationPrincipal AuthenticatedUser user,@RequestHeader("Idempotency-Key") String key,@RequestBody CompanionEconomyService.SaleRequest request){return ApiResponse.success(service.sell(user.userId(),key,request));}
}
