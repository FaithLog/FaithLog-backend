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
		assertThat(index).contains(
			"include::{snippets}/charge-admin-stale-meal-duty-recovery-success/http-request.adoc[]",
			"include::{snippets}/charge-admin-stale-meal-duty-recovery-success/http-response.adoc[]"
		);
		assertThat(index).contains(
			"`COFFEE`는 ACTIVE COFFEE 담당자가 본인 소유 계좌의 청구만",
			"`MEAL`은 일반 상태 변경 대상이 아니며",
			"서비스 전역 `ADMIN`도 정상 ACTIVE COFFEE/MEAL 청구를 우회할 수 없다"
		);
		assertThat(index).doesNotContain(
			"캠퍼스 관리자 또는 전역 `ADMIN` 사용자는 `UNPAID` 상태 청구를"
		);
	}
}
