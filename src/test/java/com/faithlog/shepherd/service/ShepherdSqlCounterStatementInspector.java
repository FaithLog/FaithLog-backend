package com.faithlog.shepherd.service;

import org.hibernate.resource.jdbc.spi.StatementInspector;

public class ShepherdSqlCounterStatementInspector implements StatementInspector {

	private static final ThreadLocal<Integer> ASSIGNEE_SELECT_COUNT = ThreadLocal.withInitial(() -> 0);

	public static void reset() {
		ASSIGNEE_SELECT_COUNT.set(0);
	}

	public static int assigneeSelectCount() {
		return ASSIGNEE_SELECT_COUNT.get();
	}

	@Override
	public String inspect(String sql) {
		String normalized = sql == null ? "" : sql.toLowerCase(java.util.Locale.ROOT);
		if (normalized.startsWith("select") && normalized.contains("shepherd_group_assignees")) {
			ASSIGNEE_SELECT_COUNT.set(ASSIGNEE_SELECT_COUNT.get() + 1);
		}
		return sql;
	}
}
