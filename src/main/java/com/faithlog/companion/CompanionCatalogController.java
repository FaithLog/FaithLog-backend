package com.faithlog.companion;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/companions")
public class CompanionCatalogController {
 private final CompanionCatalogService service;
 public CompanionCatalogController(CompanionCatalogService service){this.service=service;}
 @GetMapping("/catalog") public ApiResponse<List<CompanionCatalogService.Design>> catalog(@AuthenticationPrincipal AuthenticatedUser user){return ApiResponse.success(service.catalog(user.userId()));}
 @GetMapping("/me/inventory") public ApiResponse<CompanionCatalogService.Inventory> inventory(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(required=false) String filter,@RequestParam(required=false) String rarity,@RequestParam(required=false) String query,@RequestParam(required=false) String sort,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(service.inventory(user.userId(),new CompanionQuery(filter,rarity,query,sort,page,size)));}
 @GetMapping("/me/backgrounds") public ApiResponse<List<CompanionCatalogService.Background>> backgrounds(@AuthenticationPrincipal AuthenticatedUser user){return ApiResponse.success(service.backgrounds(user.userId()));}
}
