const BASE_FROM = `from charge_items charge
join campus_members member
	on member.campus_id = charge.campus_id
	and member.user_id = charge.user_id
	and member.status = 'ACTIVE'
join users user_account
	on user_account.id = charge.user_id
	and user_account.is_active = true
`;

const TOTAL_AMOUNT = 'coalesce(sum(charge.amount), 0)';
const statusAmount = (status) =>
	`coalesce(sum(case when charge.status = '${status}' then charge.amount else 0 end), 0)`;
const UNPAID_AMOUNT = statusAmount('UNPAID');
const PAID_AMOUNT = statusAmount('PAID');
const WAIVED_AMOUNT = statusAmount('WAIVED');
const CANCELED_AMOUNT = statusAmount('CANCELED');

const SORT_EXPRESSIONS = Object.freeze({
	createdAt: 'max(charge.created_at)',
	userId: 'charge.user_id',
	name: 'user_account.name',
	email: 'user_account.email',
	totalAmount: TOTAL_AMOUNT,
	unpaidAmount: UNPAID_AMOUNT,
	paidAmount: PAID_AMOUNT,
	waivedAmount: WAIVED_AMOUNT,
	canceledAmount: CANCELED_AMOUNT,
});

export function buildAdminChargeAggregationSql(criteria, page) {
	const whereClause = buildWhereClause(criteria);
	const { expression, direction } = resolveSort(page?.sort);
	if (!Number.isSafeInteger(page?.size) || page.size <= 0
		|| !Number.isSafeInteger(page?.offset) || page.offset < 0) {
		throw new Error('Admin charge candidate page size and offset must be non-negative integers.');
	}
	const summary = `select ${TOTAL_AMOUNT} as total_amount,
	${UNPAID_AMOUNT} as unpaid_amount,
	${PAID_AMOUNT} as paid_amount,
	${WAIVED_AMOUNT} as waived_amount,
	${CANCELED_AMOUNT} as canceled_amount
${BASE_FROM}${whereClause}`;
	const memberPage = `select charge.user_id as user_id,
	user_account.name as user_name,
	user_account.email as user_email,
	${TOTAL_AMOUNT} as total_amount,
	${UNPAID_AMOUNT} as unpaid_amount,
	${PAID_AMOUNT} as paid_amount,
	${WAIVED_AMOUNT} as waived_amount,
	${CANCELED_AMOUNT} as canceled_amount,
	max(charge.created_at) as latest_charge_created_at
${BASE_FROM}${whereClause}group by charge.user_id, user_account.name, user_account.email
order by ${expression} ${direction}, charge.user_id asc
limit :limit offset :offset
`;
	const countDistinct = `select count(distinct charge.user_id) ${BASE_FROM}${whereClause}`;
	return { summary, memberPage, countDistinct };
}

function buildWhereClause(criteria) {
	if (!criteria || !Number.isSafeInteger(criteria.campusId) || criteria.campusId <= 0) {
		throw new Error('Admin charge candidate requires a positive campusId.');
	}
	let where = 'where charge.campus_id = :campusId\n';
	if (criteria.userId !== null && criteria.userId !== undefined) {
		where += 'and charge.user_id = :userId\n';
	}
	if (typeof criteria.keyword === 'string' && criteria.keyword.trim().length > 0) {
		where += `and (
	lower(user_account.name) like :keyword escape '!'
	or lower(user_account.email) like :keyword escape '!'
)
`;
	}
	if (criteria.paymentCategory !== null && criteria.paymentCategory !== undefined) {
		where += 'and charge.payment_category = :paymentCategory\n';
	}
	if (criteria.excludedPaymentCategory !== null && criteria.excludedPaymentCategory !== undefined) {
		where += 'and charge.payment_category <> :excludedPaymentCategory\n';
	}
	if (criteria.status !== null && criteria.status !== undefined) {
		where += 'and charge.status = :status\n';
	}
	if (criteria.paymentAccountIds !== null && criteria.paymentAccountIds !== undefined) {
		if (!Array.isArray(criteria.paymentAccountIds)) {
			throw new Error('paymentAccountIds must be null or an array.');
		}
		where += criteria.paymentAccountIds.length === 0
			? 'and 1 = 0\n'
			: 'and charge.payment_account_id in (:paymentAccountIds)\n';
	}
	if (criteria.terminalCompletedAtFrom !== null && criteria.terminalCompletedAtFrom !== undefined) {
		where += `and (
	charge.status = 'UNPAID'
	or (charge.status = 'PAID' and charge.paid_at >= :terminalCompletedAtFrom)
	or (charge.status in ('WAIVED', 'CANCELED') and charge.updated_at >= :terminalCompletedAtFrom)
)
`;
	}
	return where;
}

function resolveSort(sort = 'createdAt,desc') {
	const [property, direction, extra] = String(sort).split(',');
	if (extra !== undefined || !Object.hasOwn(SORT_EXPRESSIONS, property)
		|| !['asc', 'desc'].includes(direction)) {
		throw new Error(`Unsupported admin charge member sort: ${sort}`);
	}
	return { expression: SORT_EXPRESSIONS[property], direction };
}
