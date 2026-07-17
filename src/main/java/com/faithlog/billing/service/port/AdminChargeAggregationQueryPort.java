package com.faithlog.billing.service.port;

import com.faithlog.billing.service.query.AdminChargeAggregationCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminChargeAggregationQueryPort {

	AdminChargeAggregationSummary summarize(AdminChargeAggregationCriteria criteria);

	Page<AdminChargeMemberAggregate> findMemberPage(
		AdminChargeAggregationCriteria criteria,
		Pageable pageable
	);
}
