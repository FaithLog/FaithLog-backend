package com.faithlog.billing.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BillingRestDocsIndexTest {

	@Test
	void rest_docs_index_includes_admin_charge_status_conflict_contract() throws Exception {
		String index = Files.readString(Path.of("src/docs/asciidoc/index.adoc"));

		assertThat(index).contains("include::{snippets}/charge-admin-status-change-conflict/http-request.adoc[]");
		assertThat(index).contains("include::{snippets}/charge-admin-status-change-conflict/request-fields.adoc[]");
		assertThat(index).contains("include::{snippets}/charge-admin-status-change-conflict/http-response.adoc[]");
		assertThat(index).contains("include::{snippets}/charge-admin-status-change-conflict/response-fields.adoc[]");
		assertThat(index).contains("include::{snippets}/charge-admin-stale-duty-recovery-success/http-request.adoc[]");
		assertThat(index).contains("include::{snippets}/charge-admin-stale-duty-recovery-success/request-fields.adoc[]");
		assertThat(index).contains("include::{snippets}/charge-admin-stale-duty-recovery-success/http-response.adoc[]");
	}
}
