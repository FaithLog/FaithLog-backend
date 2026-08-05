package com.faithlog.weeklymaterial.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WeeklyMaterialRestDocsIndexTest {
	@Test
	void indexIncludesAllWeeklyMaterialSnippetsAndPrivateDownloadGuidance() throws Exception {
		String index = Files.readString(Path.of("src/docs/asciidoc/index.adoc"));
		assertThat(index).contains(
			"== Weekly Materials",
			"weekly-material-put-success/http-request.adoc",
			"weekly-material-delete-success/http-request.adoc",
			"weekly-material-current-success/http-request.adoc",
			"weekly-material-current-empty-success/http-response.adoc",
			"weekly-material-week-success/http-request.adoc",
			"weekly-material-week-empty-success/http-response.adoc",
			"weekly-material-list-success/http-request.adoc",
			"기존 private media access URL API"
		);
	}
}
