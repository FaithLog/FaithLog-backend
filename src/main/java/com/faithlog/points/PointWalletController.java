package com.faithlog.points;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/points/me")
public class PointWalletController {
 private final PointWalletService service;
 public PointWalletController(PointWalletService service){this.service=service;}
 @GetMapping("/wallet") public ApiResponse<PointWalletService.WalletResult> wallet(@AuthenticationPrincipal AuthenticatedUser user){return ApiResponse.success(service.wallet(user.userId()));}
 @GetMapping("/transactions") public ApiResponse<List<PointWalletService.TransactionResult>> history(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="0") int page){return ApiResponse.success(service.history(user.userId(),page));}
}
