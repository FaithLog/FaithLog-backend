package com.faithlog.companion;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.global.exception.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class CompanionCatalogService {
 private final UserRepository users;private final NamedParameterJdbcTemplate jdbc;
 public CompanionCatalogService(UserRepository users,NamedParameterJdbcTemplate jdbc){this.users=users;this.jdbc=jdbc;}
 private void authenticate(Long id){users.findById(id).filter(u->u.isActive()).orElseThrow(()->new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));}
 public List<Design> catalog(Long id){authenticate(id);return jdbc.query("SELECT d.*,0::bigint AS quantity FROM companion_designs d WHERE active=true ORDER BY catalog_order",Map.of(),(rs,n)->design(rs));}
 public Inventory inventory(Long id,CompanionQuery q){
  authenticate(id);
  var params=new MapSqlParameterSource().addValue("user",id).addValue("rarity",q.rarity()).addValue("search",q.searchPattern()).addValue("limit",q.size()).addValue("offset",q.page()*q.size());
  String where=" FROM companion_designs d LEFT JOIN user_companions u ON u.design_id=d.design_id AND u.user_id=:user WHERE (d.active=true OR u.user_id IS NOT NULL)";
  if(q.filter().equals("OWNED"))where+=" AND u.quantity>=1";
  if(q.filter().equals("UNEQUIPPED"))where+=" AND u.quantity>=1 AND NOT EXISTS(SELECT 1 FROM garden_placements g WHERE g.user_id=:user AND g.design_id=d.design_id)";
  if(q.filter().equals("DUPLICATES"))where+=" AND u.quantity>=2";
  if(q.rarity()!=null)where+=" AND d.rarity=:rarity";
  where+=" AND (d.name ILIKE :search ESCAPE E'\\\\' OR d.species_name ILIKE :search ESCAPE E'\\\\')";
  String order=switch(q.sort()){
   case "RARITY" -> "CASE d.rarity WHEN 'MIRACLE' THEN 6 WHEN 'UNIQUE' THEN 5 WHEN 'EPIC' THEN 4 WHEN 'RARE' THEN 3 WHEN 'UNCOMMON' THEN 2 ELSE 1 END DESC,d.catalog_order";
   case "COUNT" -> "coalesce(u.quantity,0) DESC,d.catalog_order";
   default -> "d.catalog_order";
  };
  long total=jdbc.queryForObject("SELECT count(*)"+where,params,Long.class);
  var items=jdbc.query("SELECT d.*,coalesce(u.quantity,0) AS quantity"+where+" ORDER BY CASE WHEN EXISTS(SELECT 1 FROM garden_placements g WHERE g.user_id=:user AND g.design_id=d.design_id) THEN 0 ELSE 1 END,"+order+" LIMIT :limit OFFSET :offset",params,(rs,n)->design(rs));
  return new Inventory(items,q.page(),q.size(),total);
 }
 @Transactional(readOnly=false)
 public List<Background> backgrounds(Long id){authenticate(id);StarterBackgrounds.grant(jdbc,id);return jdbc.query("SELECT b.*,u.user_id IS NOT NULL AS owned FROM backgrounds b LEFT JOIN user_backgrounds u ON u.background_id=b.background_id AND u.user_id=:user WHERE b.active=true OR u.user_id IS NOT NULL ORDER BY b.background_id",Map.of("user",id),(rs,n)->new Background(rs.getString("background_id"),rs.getString("name"),rs.getString("description"),rs.getString("asset_key"),rs.getBoolean("active"),rs.getBoolean("owned")));}
 private Design design(java.sql.ResultSet rs)throws java.sql.SQLException{return new Design(rs.getString("design_id"),rs.getString("species_code"),rs.getString("name"),rs.getString("rarity"),rs.getString("asset_key"),rs.getBoolean("active"),rs.getLong("quantity"));}
 public record Design(String designId,String speciesCode,String name,String rarity,String assetKey,boolean active,long quantity){}
 public record Inventory(List<Design> items,int page,int size,long total){}
 public record Background(String backgroundId,String name,String description,String assetKey,boolean active,boolean owned){}
}
