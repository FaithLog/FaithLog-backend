import { createHash } from 'node:crypto';

const scanMetricKeys = new Map([
	['Seq Scan', 'seqScan'],
	['Index Scan', 'indexScan'],
	['Index Only Scan', 'indexOnlyScan'],
	['Bitmap Heap Scan', 'bitmapHeapScan'],
	['Bitmap Index Scan', 'bitmapIndexScan'],
]);

export function normalizeExplain(rawExplain) {
	const statement = unwrapStatement(rawExplain);
	const root = statement.Plan;
	if (!root || typeof root !== 'object') {
		throw new Error('EXPLAIN JSON must contain a Plan object.');
	}

	const nodes = [];
	visit(root, nodes);
	const structure = {
		nodes: nodes.map(structuralNode),
	};
	const scanSummary = Object.fromEntries([...scanMetricKeys.values()].map((key) => [key, 0]));
	for (const node of nodes) {
		const scanKey = scanMetricKeys.get(node['Node Type']);
		if (scanKey) {
			scanSummary[scanKey] += 1;
		}
	}

	return {
		planHash: createHash('sha256').update(stableStringify(structure)).digest('hex'),
		structure,
		metrics: {
			planningTimeMs: numberOrNull(statement['Planning Time']),
			executionTimeMs: numberOrNull(statement['Execution Time']),
			nodeTypes: nodes.map((node) => node['Node Type']),
			planRows: numberOrNull(root['Plan Rows']),
			actualRows: numberOrNull(root['Actual Rows']),
			loops: numberOrNull(root['Actual Loops']),
			sharedHitBlocks: numberOrZero(root['Shared Hit Blocks']),
			sharedReadBlocks: numberOrZero(root['Shared Read Blocks']),
			rowsRemoved: nodes.reduce((total, node) => total
				+ numberOrZero(node['Rows Removed by Filter'])
				+ numberOrZero(node['Rows Removed by Join Filter'])
				+ numberOrZero(node['Rows Removed by Index Recheck']), 0),
			scanSummary,
			nodes: nodes.map(metricNode),
		},
	};
}

function unwrapStatement(rawExplain) {
	if (Array.isArray(rawExplain)) {
		if (rawExplain.length !== 1 || typeof rawExplain[0] !== 'object') {
			throw new Error('EXPLAIN JSON must contain exactly one statement.');
		}
		return rawExplain[0];
	}
	if (rawExplain && typeof rawExplain === 'object') {
		return rawExplain;
	}
	throw new Error('EXPLAIN JSON must be an object or a single-element array.');
}

function visit(node, nodes) {
	nodes.push(node);
	for (const child of node.Plans || []) {
		visit(child, nodes);
	}
}

function structuralNode(node) {
	return compact({
		nodeType: node['Node Type'],
		parentRelationship: node['Parent Relationship'],
		joinType: node['Join Type'],
		relationName: node['Relation Name'],
		alias: node.Alias,
		indexName: node['Index Name'],
		scanDirection: node['Scan Direction'],
		strategy: node.Strategy,
		partialMode: node['Partial Mode'],
		sortKey: normalizeExpressionList(node['Sort Key']),
		groupKey: normalizeExpressionList(node['Group Key']),
		indexCond: normalizeExpression(node['Index Cond']),
		hashCond: normalizeExpression(node['Hash Cond']),
		mergeCond: normalizeExpression(node['Merge Cond']),
		joinFilter: normalizeExpression(node['Join Filter']),
		filter: normalizeExpression(node.Filter),
		recheckCond: normalizeExpression(node['Recheck Cond']),
	});
}

function metricNode(node) {
	return compact({
		nodeType: node['Node Type'],
		relationName: node['Relation Name'],
		indexName: node['Index Name'],
		planRows: numberOrNull(node['Plan Rows']),
		actualRows: numberOrNull(node['Actual Rows']),
		loops: numberOrNull(node['Actual Loops']),
		sharedHitBlocks: numberOrZero(node['Shared Hit Blocks']),
		sharedReadBlocks: numberOrZero(node['Shared Read Blocks']),
		rowsRemovedByFilter: numberOrZero(node['Rows Removed by Filter']),
		rowsRemovedByJoinFilter: numberOrZero(node['Rows Removed by Join Filter']),
		rowsRemovedByIndexRecheck: numberOrZero(node['Rows Removed by Index Recheck']),
		sortMethod: node['Sort Method'],
		sortSpaceUsedKb: numberOrNull(node['Sort Space Used']),
		sortSpaceType: node['Sort Space Type'],
	});
}

function normalizeExpressionList(value) {
	return Array.isArray(value) ? value.map(normalizeExpression) : undefined;
}

function normalizeExpression(value) {
	if (typeof value !== 'string') {
		return undefined;
	}
	return value
		.replace(/'(?:''|[^'])*'/g, '?')
		.replace(/\b\d+(?:\.\d+)?\b/g, '?')
		.replace(/\s+/g, ' ')
		.trim();
}

function numberOrNull(value) {
	return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function numberOrZero(value) {
	return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function compact(value) {
	return Object.fromEntries(Object.entries(value).filter(([, field]) => field !== undefined && field !== null));
}

function stableStringify(value) {
	if (Array.isArray(value)) {
		return `[${value.map(stableStringify).join(',')}]`;
	}
	if (value && typeof value === 'object') {
		return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
	}
	return JSON.stringify(value);
}
