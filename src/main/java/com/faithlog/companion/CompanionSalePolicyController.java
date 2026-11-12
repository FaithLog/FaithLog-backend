package com.faithlog.companion;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.points.PointWalletService;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/companions/sale-prices")
public class CompanionSalePolicyController {
 private final NamedParameterJdbcTemplate jdbc;private final PointWalletService wallet;
 public CompanionSalePolicyController(NamedParameterJdbcTemplate jdbc,PointWalletService wallet){this.jdbc=jdbc;this.wallet=wallet;}
 @GetMapping @Transactional public ApiResponse<List<Price>> prices(@AuthenticationPrincipal AuthenticatedUser user){
  wallet.lockAccount(user.userId());
  return ApiResponse.success(jdbc.query("SELECT rarity,unit_price,policy_version FROM companion_sale_prices ORDER BY rarity",Map.of(),(rs,n)->new Price(rs.getString("rarity"),rs.getLong("unit_price"),rs.getString("policy_version"))));
 }
 public record Price(String rarity,long unitPrice,String policyVersion){}
}
