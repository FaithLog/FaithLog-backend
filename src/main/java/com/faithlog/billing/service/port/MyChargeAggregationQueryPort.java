package com.faithlog.billing.service.port;

import com.faithlog.billing.domain.type.*;
import com.faithlog.billing.service.result.ChargeAmountSummaryResult;
import java.time.Instant;

public interface MyChargeAggregationQueryPort {
 ChargeAmountSummaryResult summarize(Long campus,Long user,PaymentCategory category,ChargeStatus status,Instant terminalFrom);
}
