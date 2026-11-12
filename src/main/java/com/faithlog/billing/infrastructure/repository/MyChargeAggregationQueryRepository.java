package com.faithlog.billing.infrastructure.repository;

import com.faithlog.billing.domain.type.*;
import com.faithlog.billing.service.port.MyChargeAggregationQueryPort;
import com.faithlog.billing.service.result.ChargeAmountSummaryResult;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class MyChargeAggregationQueryRepository implements MyChargeAggregationQueryPort {
 private final NamedParameterJdbcTemplate jdbc;
 public MyChargeAggregationQueryRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
 public ChargeAmountSummaryResult summarize(Long campus,Long user,PaymentCategory category,ChargeStatus status,Instant terminalFrom){
  var p=new MapSqlParameterSource("campus",campus).addValue("user",user);
  var where=new StringBuilder(" FROM charge_items WHERE campus_id=:campus AND user_id=:user");
  if(category!=null){where.append(" AND payment_category=:category");p.addValue("category",category.name());}
  if(status!=null){where.append(" AND status=:status");p.addValue("status",status.name());}
  if(terminalFrom!=null){where.append(" AND (status='UNPAID' OR (status='PAID' AND paid_at>=:from) OR (status IN ('WAIVED','CANCELED') AND updated_at>=:from))");p.addValue("from",Timestamp.from(terminalFrom));}
  String sql="SELECT coalesce(sum(amount),0) AS total,"+amount("UNPAID")+" AS unpaid,"+amount("PAID")+" AS paid,"+amount("WAIVED")+" AS waived,"+amount("CANCELED")+" AS canceled"+where;
  return jdbc.queryForObject(sql,p,(rs,n)->new ChargeAmountSummaryResult((int)rs.getLong("total"),(int)rs.getLong("unpaid"),(int)rs.getLong("paid"),(int)rs.getLong("waived"),(int)rs.getLong("canceled")));
 }
 private static String amount(String status){return "coalesce(sum(case when status='"+status+"' then amount else 0 end),0)";}
}
