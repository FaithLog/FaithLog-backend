package com.faithlog.billing.infrastructure.repository;

import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.service.port.AdminChargeAggregationQueryPort;
import com.faithlog.billing.service.port.AdminChargeAggregationSummary;
import com.faithlog.billing.service.port.AdminChargeMemberAggregate;
import com.faithlog.billing.service.query.AdminChargeAggregationCriteria;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminChargeAggregationQueryRepository implements AdminChargeAggregationQueryPort {

	private static final String BASE_FROM = """
		from charge_items charge
		join campus_members member
			on member.campus_id = charge.campus_id
			and member.user_id = charge.user_id
			and member.status = 'ACTIVE'
		join users user_account
			on user_account.id = charge.user_id
			and user_account.is_active = true
		""";
	private static final String TOTAL_AMOUNT = "coalesce(sum(charge.amount), 0)";
	private static final String UNPAID_AMOUNT = statusAmount(ChargeStatus.UNPAID);
	private static final String PAID_AMOUNT = statusAmount(ChargeStatus.PAID);
	private static final String WAIVED_AMOUNT = statusAmount(ChargeStatus.WAIVED);
	private static final String CANCELED_AMOUNT = statusAmount(ChargeStatus.CANCELED);

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public AdminChargeAggregationQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public AdminChargeAggregationSummary summarize(AdminChargeAggregationCriteria criteria) {
		QueryParts query = queryParts(criteria);
		String sql = """
			select %s as total_amount,
				%s as unpaid_amount,
				%s as paid_amount,
				%s as waived_amount,
				%s as canceled_amount
			""".formatted(
			TOTAL_AMOUNT,
			UNPAID_AMOUNT,
			PAID_AMOUNT,
			WAIVED_AMOUNT,
			CANCELED_AMOUNT
		) + BASE_FROM + query.whereClause();
		return jdbcTemplate.queryForObject(sql, query.parameters(), (resultSet, rowNumber) ->
			new AdminChargeAggregationSummary(
				resultSet.getLong("total_amount"),
				resultSet.getLong("unpaid_amount"),
				resultSet.getLong("paid_amount"),
				resultSet.getLong("waived_amount"),
				resultSet.getLong("canceled_amount")
			)
		);
	}

	@Override
	public Page<AdminChargeMemberAggregate> findMemberPage(
		AdminChargeAggregationCriteria criteria,
		Pageable pageable
	) {
		QueryParts query = queryParts(criteria);
		MapSqlParameterSource pageParameters = copy(query.parameters())
			.addValue("limit", pageable.getPageSize())
			.addValue("offset", pageable.getOffset());
		String contentSql = """
			select charge.user_id as user_id,
				user_account.name as user_name,
				user_account.email as user_email,
				%s as total_amount,
				%s as unpaid_amount,
				%s as paid_amount,
				%s as waived_amount,
				%s as canceled_amount,
				max(charge.created_at) as latest_charge_created_at
			""".formatted(
			TOTAL_AMOUNT,
			UNPAID_AMOUNT,
			PAID_AMOUNT,
			WAIVED_AMOUNT,
			CANCELED_AMOUNT
		) + BASE_FROM + query.whereClause() + """
			group by charge.user_id, user_account.name, user_account.email
			order by %s %s, charge.user_id asc
			limit :limit offset :offset
			""".formatted(sortExpression(pageable.getSort()), sortDirection(pageable.getSort()));
		List<AdminChargeMemberAggregate> content = jdbcTemplate.query(
			contentSql,
			pageParameters,
			(resultSet, rowNumber) -> {
				Timestamp latestCreatedAt = resultSet.getTimestamp("latest_charge_created_at");
				return new AdminChargeMemberAggregate(
					resultSet.getLong("user_id"),
					resultSet.getString("user_name"),
					resultSet.getString("user_email"),
					resultSet.getLong("total_amount"),
					resultSet.getLong("unpaid_amount"),
					resultSet.getLong("paid_amount"),
					resultSet.getLong("waived_amount"),
					resultSet.getLong("canceled_amount"),
					latestCreatedAt == null ? null : latestCreatedAt.toInstant()
				);
			}
		);
		String countSql = "select count(distinct charge.user_id) " + BASE_FROM + query.whereClause();
		Long total = jdbcTemplate.queryForObject(countSql, query.parameters(), Long.class);
		return new PageImpl<>(content, pageable, total == null ? 0 : total);
	}

	private QueryParts queryParts(AdminChargeAggregationCriteria criteria) {
		StringBuilder where = new StringBuilder("where charge.campus_id = :campusId\n");
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("campusId", criteria.campusId());
		if (criteria.userId() != null) {
			where.append("and charge.user_id = :userId\n");
			parameters.addValue("userId", criteria.userId());
		}
		if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
			where.append("""
				and (
					lower(user_account.name) like :keyword escape '!'
					or lower(user_account.email) like :keyword escape '!'
				)
				""");
			parameters.addValue("keyword", containsPattern(criteria.keyword()));
		}
		if (criteria.paymentCategory() != null) {
			where.append("and charge.payment_category = :paymentCategory\n");
			parameters.addValue("paymentCategory", criteria.paymentCategory().name());
		}
		if (criteria.excludedPaymentCategory() != null) {
			where.append("and charge.payment_category <> :excludedPaymentCategory\n");
			parameters.addValue("excludedPaymentCategory", criteria.excludedPaymentCategory().name());
		}
		if (criteria.status() != null) {
			where.append("and charge.status = :status\n");
			parameters.addValue("status", criteria.status().name());
		}
		if (criteria.paymentAccountIds() != null) {
			if (criteria.paymentAccountIds().isEmpty()) {
				where.append("and 1 = 0\n");
			} else {
				where.append("and charge.payment_account_id in (:paymentAccountIds)\n");
				parameters.addValue("paymentAccountIds", criteria.paymentAccountIds());
			}
		}
		if (criteria.terminalCompletedAtFrom() != null) {
			where.append("""
				and (
					charge.status = 'UNPAID'
					or (charge.status = 'PAID' and charge.paid_at >= :terminalCompletedAtFrom)
					or (charge.status in ('WAIVED', 'CANCELED') and charge.updated_at >= :terminalCompletedAtFrom)
				)
				""");
			parameters.addValue(
				"terminalCompletedAtFrom",
				Timestamp.from(criteria.terminalCompletedAtFrom())
			);
		}
		return new QueryParts(where.toString(), parameters);
	}

	private String sortExpression(Sort sort) {
		String property = primaryOrder(sort).getProperty();
		return switch (property) {
			case "createdAt" -> "max(charge.created_at)";
			case "userId" -> "charge.user_id";
			case "name" -> "user_account.name";
			case "email" -> "user_account.email";
			case "totalAmount" -> TOTAL_AMOUNT;
			case "unpaidAmount" -> UNPAID_AMOUNT;
			case "paidAmount" -> PAID_AMOUNT;
			case "waivedAmount" -> WAIVED_AMOUNT;
			case "canceledAmount" -> CANCELED_AMOUNT;
			default -> throw new IllegalArgumentException("Unsupported admin charge member sort: " + property);
		};
	}

	private String sortDirection(Sort sort) {
		return primaryOrder(sort).isAscending() ? "asc" : "desc";
	}

	private Sort.Order primaryOrder(Sort sort) {
		return sort.stream().findFirst().orElse(Sort.Order.desc("createdAt"));
	}

	private String containsPattern(String keyword) {
		String escaped = keyword.toLowerCase(Locale.ROOT)
			.replace("!", "!!")
			.replace("%", "!%")
			.replace("_", "!_");
		return "%" + escaped + "%";
	}

	private MapSqlParameterSource copy(MapSqlParameterSource source) {
		MapSqlParameterSource copy = new MapSqlParameterSource();
		for (String parameterName : source.getParameterNames()) {
			copy.addValue(parameterName, source.getValue(parameterName));
		}
		return copy;
	}

	private static String statusAmount(ChargeStatus status) {
		return "coalesce(sum(case when charge.status = '" + status.name()
			+ "' then charge.amount else 0 end), 0)";
	}

	private record QueryParts(String whereClause, MapSqlParameterSource parameters) {
	}
}
