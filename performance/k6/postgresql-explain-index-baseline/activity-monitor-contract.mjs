export function validateActivityWindow(window, { expectedLabels = null } = {}) {
	const reasons = [];
	if (!window || window.sampleCount < 2) reasons.push('activity-monitor-samples-incomplete');
	if (window?.measuredSessionObserved !== true) reasons.push('measured-session-not-observed-by-monitor');
	if (window?.transientExternalActivityDetected || (window?.sessions || []).length > 0) {
		reasons.push('transient-external-activity-detected');
	}
	if (typeof window?.measuredDatabase === 'string'
		&& (window?.sessions || []).some((session) => session.database !== window.measuredDatabase)) {
		reasons.push('other-database-activity-detected');
	}
	if (expectedLabels) {
		const measuredSessions = Array.isArray(window?.measuredSessions) ? window.measuredSessions : [];
		const actualLabels = measuredSessions.map((session) => session.label).sort();
		const requiredLabels = [...expectedLabels].sort();
		const tokens = measuredSessions.map((session) => session.registrationToken);
		if (JSON.stringify(actualLabels) !== JSON.stringify(requiredLabels)) {
			reasons.push('measured-registration-labels-incomplete');
		}
		if (tokens.some((token) => !/^[a-f0-9]{32}$/.test(token ?? ''))
			|| new Set(tokens).size !== tokens.length) {
			reasons.push('measured-registration-token-invalid');
		}
		if (measuredSessions.some((session) => session.observed !== true || session.unregistered !== true)) {
			reasons.push('measured-session-lifecycle-incomplete');
		}
	}
	return { adoptable: reasons.length === 0, reasons };
}
