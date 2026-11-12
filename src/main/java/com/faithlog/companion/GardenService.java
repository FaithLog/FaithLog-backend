package com.faithlog.companion;
import com.faithlog.points.PointWalletService;
import com.faithlog.global.exception.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class GardenService {
 private final PointWalletService accounts;private final NamedParameterJdbcTemplate jdbc;private final Clock clock;
 public GardenService(PointWalletService accounts,NamedParameterJdbcTemplate jdbc,Clock clock){this.accounts=accounts;this.jdbc=jdbc;this.clock=clock;}
 private void initialize(Long user){
  accounts.lockAccount(user);
  jdbc.update("INSERT INTO user_gardens(user_id,version,updated_at) VALUES(:user,0,:now) ON CONFLICT(user_id) DO NOTHING",new MapSqlParameterSource("user",user).addValue("now",java.sql.Timestamp.from(clock.instant())));
 }
 @Transactional public Garden get(Long user){initialize(user);return read(user);}
 private Garden read(Long user){
  var params=Map.of("user",user);
  var placements=jdbc.query("SELECT slot,design_id,x,y FROM garden_placements WHERE user_id=:user ORDER BY slot",params,(rs,n)->new GardenUpdate.Placement(rs.getInt("slot"),rs.getString("design_id"),rs.getDouble("x"),rs.getDouble("y")));
  return jdbc.queryForObject("SELECT version,background_id FROM user_gardens WHERE user_id=:user",params,(rs,n)->new Garden(rs.getLong("version"),rs.getString("background_id"),placements));
 }
 @Transactional public Garden save(Long user,GardenUpdate request){
  initialize(user);
  var current=read(user);
  if(current.version()!=request.version())throw new BusinessException(ErrorCode.COMPANION_GARDEN_CONFLICT);
  var params=new MapSqlParameterSource("user",user).addValue("background",request.backgroundId());
  if(request.backgroundId()!=null){
   long owned=jdbc.queryForObject("SELECT count(*) FROM user_backgrounds WHERE user_id=:user AND background_id=:background",params,Long.class);
   if(owned!=1)throw new BusinessException(ErrorCode.COMPANION_NOT_OWNED);
  }
  if(!request.placements().isEmpty()){
   params.addValue("designs",request.placements().stream().map(GardenUpdate.Placement::designId).toList());
   long owned=jdbc.queryForObject("SELECT count(*) FROM user_companions WHERE user_id=:user AND design_id IN (:designs) AND quantity>=1",params,Long.class);
   if(owned!=request.placements().size())throw new BusinessException(ErrorCode.COMPANION_NOT_OWNED);
  }
  params.addValue("version",request.version()).addValue("now",java.sql.Timestamp.from(clock.instant()));
  int changed=jdbc.update("UPDATE user_gardens SET background_id=:background,version=version+1,updated_at=:now WHERE user_id=:user AND version=:version",params);
  if(changed!=1)throw new BusinessException(ErrorCode.COMPANION_GARDEN_CONFLICT);
  jdbc.update("DELETE FROM garden_placements WHERE user_id=:user",params);
  for(var p:request.placements())jdbc.update("INSERT INTO garden_placements(user_id,slot,design_id,x,y) VALUES(:user,:slot,:design,:x,:y)",new MapSqlParameterSource("user",user).addValue("slot",p.slot()).addValue("design",p.designId()).addValue("x",p.x()).addValue("y",p.y()));
  return read(user);
 }
 public record Garden(long version,String backgroundId,List<GardenUpdate.Placement> placements){}
}
