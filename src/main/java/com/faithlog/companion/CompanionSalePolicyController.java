package com.faithlog.companion;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/companions/sale-prices")
public class CompanionSalePolicyController {
 private final NamedParameterJdbcTemplate jdbc;
 public CompanionSalePolicyController(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping @Transactional(readOnly=true) public ApiResponse<List<Price>> prices(@AuthenticationPrincipal AuthenticatedUser user){
  Long active=jdbc.queryForObject("SELECT count(*) FROM users WHERE id=:user AND is_active=true AND deleted_at IS NULL",
     Map.of("user",user.userId()),Long.class);
  if(active==null||active!=1L)throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
  return ApiResponse.success(jdbc.query("SELECT rarity,unit_price,policy_version FROM companion_sale_prices ORDER BY rarity",Map.of(),(rs,n)->new Price(rs.getString("rarity"),rs.getLong("unit_price"),rs.getString("policy_version"))));
 }
 public record Price(String rarity,long unitPrice,String policyVersion){}
}
