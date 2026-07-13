package com.faithlog.devotion.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DevotionRestDocsIndexTest {

	@Test
	void rest_docs_index_includes_admin_weekly_members_and_excel_export() throws Exception {
		String index = Files.readString(Path.of("src/docs/asciidoc/index.adoc"));

		assertThat(index).contains(
			"include::{snippets}/devotion-admin-weekly-members-success/http-request.adoc[]"
		);
		assertThat(index).contains(
			"include::{snippets}/devotion-admin-weekly-export-success/response-headers.adoc[]"
		);
	}
}
