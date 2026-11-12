package com.faithlog.companion;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.points.PointWalletService;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/companions/draw-pools")
public class CompanionPoolController {
 private final NamedParameterJdbcTemplate jdbc;private final PointWalletService wallet;
 public CompanionPoolController(NamedParameterJdbcTemplate jdbc,PointWalletService wallet){this.jdbc=jdbc;this.wallet=wallet;}
 @GetMapping @Transactional public ApiResponse<List<Pool>> pools(@AuthenticationPrincipal AuthenticatedUser user){
  wallet.lockAccount(user.userId());
  var entries=jdbc.query("SELECT p.pool_version,p.name,p.unit_cost,e.design_id,e.weight,sum(e.weight) OVER(PARTITION BY p.pool_version) AS total FROM companion_pool_versions p JOIN companion_pool_entries e ON e.pool_version=p.pool_version WHERE p.active=true ORDER BY p.pool_version,e.design_id",Map.of(),(rs,n)->new Entry(rs.getString("pool_version"),rs.getString("name"),rs.getLong("unit_cost"),rs.getString("design_id"),rs.getInt("weight"),rs.getLong("total")));
  var groups=new LinkedHashMap<String,List<Entry>>();for(var e:entries)groups.computeIfAbsent(e.poolVersion(),k->new ArrayList<>()).add(e);
  return ApiResponse.success(groups.values().stream().map(es->new Pool(es.getFirst().poolVersion(),es.getFirst().name(),es.getFirst().unitCost(),es.getFirst().totalWeight(),es.stream().map(e->new Chance(e.designId(),e.weight())).toList())).toList());
 }
 private record Entry(String poolVersion,String name,long unitCost,String designId,int weight,long totalWeight){}
 public record Pool(String poolVersion,String name,long unitCost,long totalWeight,List<Chance> entries){}
 public record Chance(String designId,int weight){}
}
