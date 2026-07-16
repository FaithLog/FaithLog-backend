package com.faithlog.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class BillingPageRequestsTest {

	private static final List<String> CHARGE_ITEM_SORT_PROPERTIES = List.of(
		"createdAt", "dueDate", "paidAt", "amount", "status", "paymentCategory"
	);

	@Test
	void charge_items_adds_same_direction_id_tie_break_for_every_allowed_primary_sort() {
		for (String property : CHARGE_ITEM_SORT_PROPERTIES) {
			assertChargeItemSort(property, "asc", Sort.Direction.ASC);
			assertChargeItemSort(property, "desc", Sort.Direction.DESC);
		}
	}

	@Test
	void admin_members_keeps_its_existing_single_primary_sort() {
		Pageable pageable = BillingPageRequests.adminMembers(0, 20, "createdAt,desc");

		assertThat(pageable.getSort().toList())
			.containsExactly(Sort.Order.desc("createdAt"));
	}

	private void assertChargeItemSort(String property, String direction, Sort.Direction expectedDirection) {
		Pageable pageable = BillingPageRequests.chargeItems(0, 20, property + "," + direction);

		assertThat(pageable.getSort().toList()).containsExactly(
			new Sort.Order(expectedDirection, property),
			new Sort.Order(expectedDirection, "id")
		);
	}
}
