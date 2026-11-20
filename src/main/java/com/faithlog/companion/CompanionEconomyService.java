package com.faithlog.companion;
import com.faithlog.points.PointWalletService;
import com.faithlog.global.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CompanionEconomyService {
 private final PointWalletService wallet;private final NamedParameterJdbcTemplate jdbc;private final ObjectMapper json;private final Clock clock;
 private final SecureRandom random=new SecureRandom();
 public CompanionEconomyService(PointWalletService wallet,NamedParameterJdbcTemplate jdbc,ObjectMapper json,Clock clock){this.wallet=wallet;this.jdbc=jdbc;this.json=json;this.clock=clock;}
 private BusinessException invalid(){return new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);}
 private void key(String key){if(key==null||!key.matches("[A-Za-z0-9_-]{1,80}"))throw invalid();}
 private Result replay(Long user,String kind,String key,String fingerprint){
  var rows=jdbc.queryForList("SELECT request_fingerprint,response_json FROM companion_operations WHERE user_id=:user AND kind=:kind AND request_key=:key",Map.of("user",user,"kind",kind,"key",key));
  if(rows.isEmpty())return null;
  if(!fingerprint.equals(rows.getFirst().get("request_fingerprint")))throw new BusinessException(ErrorCode.COMPANION_REQUEST_CONFLICT);
  try{return json.readValue((String)rows.getFirst().get("response_json"),Result.class);}catch(Exception e){throw new IllegalStateException("Stored operation unreadable",e);}
 }
 private void record(Long user,String kind,String key,String fingerprint,String pool,String business,Result result){
  try{jdbc.update("INSERT INTO companion_operations(operation_id,user_id,kind,request_key,request_fingerprint,response_json,business_key,pool_version,created_at) VALUES(:id,:user,:kind,:key,:fingerprint,:response,:business,:pool,:now)",new MapSqlParameterSource("id",result.operationId()).addValue("user",user).addValue("kind",kind).addValue("key",key).addValue("fingerprint",fingerprint).addValue("response",json.writeValueAsString(result)).addValue("business",business).addValue("pool",pool).addValue("now",java.sql.Timestamp.from(clock.instant())));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
 @Transactional public Result draw(Long user,String key,DrawRequest request){
  key(key);if(request==null||request.count()!=1&&request.count()!=10||request.poolVersion()==null||!request.poolVersion().matches("[a-zA-Z0-9_-]{1,80}"))throw invalid();
  wallet.lockAccount(user);String fp=request.poolVersion()+":"+request.count();var old=replay(user,"DRAW",key,fp);if(old!=null)return old;
  var p=new MapSqlParameterSource("pool",request.poolVersion());
  var costs=jdbc.queryForList("SELECT unit_cost FROM companion_pool_versions WHERE pool_version=:pool AND active=true",p,Long.class);if(costs.isEmpty())throw invalid();
  long cost=Math.multiplyExact(costs.getFirst(),request.count());if(wallet.wallet(user).balance()<cost)throw new BusinessException(ErrorCode.COMPANION_INSUFFICIENT_POINTS);
  var entries=jdbc.query("SELECT design_id,weight FROM companion_pool_entries WHERE pool_version=:pool ORDER BY design_id",p,(rs,n)->new DrawPolicy.Entry(rs.getString("design_id"),rs.getInt("weight")));
  var policy=new DrawPolicy(entries);var results=new ArrayList<String>();for(int i=0;i<request.count();i++)results.add(policy.draw(random));
  String business="draw:"+key;wallet.settle(user,-cost,business);
  for(String design:results)jdbc.update("INSERT INTO user_companions(user_id,design_id,quantity,acquired_at) VALUES(:user,:design,1,:now) ON CONFLICT(user_id,design_id) DO UPDATE SET quantity=user_companions.quantity+1",new MapSqlParameterSource("user",user).addValue("design",design).addValue("now",java.sql.Timestamp.from(clock.instant())));
  var result=new Result(UUID.randomUUID(),List.copyOf(results),-cost,wallet.wallet(user).balance(),request.poolVersion(),0,0);
  record(user,"DRAW",key,fp,request.poolVersion(),business,result);return result;
 }
 /** The same eligibility predicate is shared by the sale endpoint and UI quote. */
 private long untouchedUnplaced(Long user,String design){
  var p=new MapSqlParameterSource("user",user).addValue("design",design);
  Long n=jdbc.queryForObject("""
      SELECT count(*) FROM companion_instances ci
      WHERE ci.user_id=:user AND ci.design_id=:design
        AND ci.level=1 AND ci.total_earned_xp=0 AND ci.care_days=0
        AND NOT EXISTS (
          SELECT 1 FROM garden_placements gp
          WHERE gp.user_id=ci.user_id AND gp.instance_id=ci.id
        )
      """,p,Long.class);
  return n==null?0L:n;
 }

 /** Advisory quantity for display; sell() rechecks under the existing lock. */
 @Transactional(readOnly=true)
 public SaleAvailability saleAvailability(Long user,String design){
  if(design==null||design.isBlank()||design.length()>100)throw invalid();
  Long active=jdbc.queryForObject("""
     SELECT count(*) FROM users WHERE id=:user AND is_active=true AND deleted_at IS NULL
     """,Map.of("user",user),Long.class);
  if(active==null||active!=1L)throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
  var p=new MapSqlParameterSource("user",user).addValue("design",design);
  Long owned=jdbc.queryForObject("""
     SELECT COALESCE((SELECT quantity FROM user_companions
                       WHERE user_id=:user AND design_id=:design),0)
     """,p,Long.class);
  long quantity=owned==null?0L:owned;
  long available=Math.min(untouchedUnplaced(user,design),Math.max(0L,quantity-1));
  return new SaleAvailability(design,quantity,available);
 }
 public record SaleAvailability(String designId,long ownedQuantity,long sellableQuantity){}
 @Transactional public Result sell(Long user,String key,SaleRequest request){
  key(key);if(request==null||request.designId()==null||request.designId().length()>100||request.quantity()<1)throw invalid();
  wallet.lockAccount(user);String fp=request.designId()+":"+request.quantity();var old=replay(user,"SALE",key,fp);if(old!=null)return old;
  var p=new MapSqlParameterSource("user",user).addValue("design",request.designId());
  var rows=jdbc.queryForList("SELECT u.quantity,p.unit_price,p.policy_version FROM user_companions u JOIN companion_designs d ON d.design_id=u.design_id JOIN companion_sale_prices p ON p.rarity=d.rarity WHERE u.user_id=:user AND u.design_id=:design",p);
  if(rows.isEmpty())throw new BusinessException(ErrorCode.COMPANION_INVALID_SALE);
  var row=rows.getFirst();SaleQuote quote;
  try{quote=SaleQuote.calculate(((Number)row.get("quantity")).longValue(),request.quantity(),((Number)row.get("unit_price")).longValue());}catch(IllegalArgumentException|ArithmeticException e){throw new BusinessException(ErrorCode.COMPANION_INVALID_SALE);}
  // Keep the legacy quantity API, but never sell a cared-for, evolved or
  // uniquely deployed instance by accident. Guard before the database trigger
  // so clients receive the existing 409 sale error rather than a SQL 500.
  long sellable=untouchedUnplaced(user,request.designId());
  if(sellable<quote.quantity())
   throw new BusinessException(ErrorCode.COMPANION_INVALID_SALE);
  p.addValue("quantity",quote.quantity());jdbc.update("UPDATE user_companions SET quantity=quantity-:quantity WHERE user_id=:user AND design_id=:design",p);
  String business="sale:"+key;wallet.settle(user,quote.totalPoints(),business);
  var result=new Result(UUID.randomUUID(),List.of(request.designId()),quote.totalPoints(),wallet.wallet(user).balance(),(String)row.get("policy_version"),quote.quantity(),quote.unitPrice());
  record(user,"SALE",key,fp,null,business,result);return result;
 }
 @Transactional(readOnly=true)
 public long growthShardBalance(Long user) {
  return jdbc.queryForObject("""
      SELECT COALESCE((SELECT balance FROM companion_growth_shards WHERE user_id=:user),0)
      """,Map.of("user",user),Long.class);
 }

 @Transactional
 public ShardExchange exchange(Long user,String key,ShardExchangeRequest request) {
  key(key);
  if(request==null||request.designId()==null||request.designId().isBlank()
      ||request.designId().length()>100)throw invalid();
  wallet.lockAccount(user);
  var params=new MapSqlParameterSource("user",user).addValue("key",key)
      .addValue("design",request.designId());
  var prior=jdbc.query("""
      SELECT design_id,awarded,resulting_balance FROM companion_shard_exchanges
      WHERE user_id=:user AND request_key=:key
      """,params,(rs,n)->new ShardExchange(rs.getString(1),rs.getLong(2),rs.getLong(3)));
  if(!prior.isEmpty()) {
   var result=prior.getFirst();
   if(!result.designId().equals(request.designId()))
    throw new BusinessException(ErrorCode.COMPANION_REQUEST_CONFLICT);
   return result;
  }
  var rows=jdbc.query("""
      SELECT d.rarity FROM user_companions u JOIN companion_designs d ON d.design_id=u.design_id
      WHERE u.user_id=:user AND u.design_id=:design AND u.quantity>1
      """,params,(rs,n)->rs.getString(1));
  if(rows.isEmpty()||untouchedUnplaced(user,request.designId())<1)
   throw new BusinessException(ErrorCode.COMPANION_INVALID_SHARD_EXCHANGE);
  int award=switch(rows.getFirst()) {
   case "COMMON" -> 1;
   case "UNCOMMON" -> 2;
   case "RARE" -> 4;
   case "EPIC" -> 8;
   case "UNIQUE" -> 16;
   default -> throw new BusinessException(ErrorCode.COMPANION_INVALID_SHARD_EXCHANGE);
  };
  jdbc.update("UPDATE user_companions SET quantity=quantity-1 WHERE user_id=:user AND design_id=:design",params);
  params.addValue("award",award);
  long balance=jdbc.queryForObject("""
      INSERT INTO companion_growth_shards(user_id,balance) VALUES(:user,:award)
      ON CONFLICT(user_id) DO UPDATE SET balance=companion_growth_shards.balance+EXCLUDED.balance
      RETURNING balance
      """,params,Long.class);
  jdbc.update("""
      INSERT INTO companion_shard_exchanges(user_id,request_key,design_id,awarded,resulting_balance)
      VALUES(:user,:key,:design,:award,:balance)
      """,params.addValue("balance",balance));
  return new ShardExchange(request.designId(),award,balance);
 }
 public record ShardExchangeRequest(String designId){}
 public record ShardExchange(String designId,long awarded,long balance){}
 @Transactional(readOnly=true) public List<HistoryItem> drawHistory(Long user,int page) {
  if(page<0||page>10000)throw invalid();
  Long active=jdbc.queryForObject(
    "SELECT count(*) FROM users WHERE id=:user AND is_active=true AND deleted_at IS NULL",
    Map.of("user",user),Long.class);
  if(active==null||active!=1L)throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
  return jdbc.query("SELECT response_json,created_at FROM companion_operations WHERE user_id=:user AND kind='DRAW' ORDER BY created_at DESC,operation_id DESC LIMIT 20 OFFSET :offset",Map.of("user",user,"offset",page*20),(rs,n)->{
   try{return new HistoryItem(json.readValue(rs.getString("response_json"),Result.class),rs.getTimestamp("created_at").toInstant());}
   catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Stored operation unreadable",e);}
  });
 }
 public record HistoryItem(Result result,Instant createdAt){}
 public record DrawRequest(String poolVersion,int count){}
 public record SaleRequest(String designId,long quantity){}
 public record Result(UUID operationId,List<String> designIds,long pointDelta,long balance,String policyVersion,long quantity,long unitPrice){}
}
