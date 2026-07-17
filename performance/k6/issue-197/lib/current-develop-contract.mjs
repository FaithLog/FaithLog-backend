const TERMINAL_RETENTION_STATUSES = new Set(['PAID', 'WAIVED', 'CANCELED']);

export function isDefaultArchiveVisible(charge, cutoffInstant) {
	const cutoff = instant(cutoffInstant, 'cutoffInstant');
	if (charge?.status === 'UNPAID') return true;
	if (charge?.status === 'PAID') return optionalInstant(charge.paidAt) >= cutoff;
	if (charge?.status === 'WAIVED' || charge?.status === 'CANCELED') {
		return optionalInstant(charge.updatedAt) >= cutoff;
	}
	return false;
}

export function isAnnualTerminalRetentionCandidate(charge, previousYear) {
	if (!TERMINAL_RETENTION_STATUSES.has(charge?.status)) return false;
	const createdAt = optionalInstant(charge.createdAt);
	if (!Number.isFinite(createdAt)) return false;
	const startInclusive = instant(previousYear?.startInclusive, 'previousYear.startInclusive');
	const endExclusive = instant(previousYear?.endExclusive, 'previousYear.endExclusive');
	if (startInclusive >= endExclusive) throw new Error('previousYear range must be increasing.');
	return createdAt >= startInclusive && createdAt < endExclusive;
}

function instant(value, label) {
	const parsed = optionalInstant(value);
	if (!Number.isFinite(parsed)) throw new Error(`${label} must be an ISO instant.`);
	return parsed;
}

function optionalInstant(value) {
	return typeof value === 'string' && value.length > 0 ? Date.parse(value) : Number.NaN;
}
