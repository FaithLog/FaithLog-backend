package com.faithlog.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetKind;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MediaAssetPdfContractTest {

	private static final long THIRTY_MIB = 30L * 1024 * 1024;

	@Test
	void reserves_pdf_at_exact_30_mib_with_safe_display_name() {
		MediaAsset asset = MediaAsset.reserve(7L, 11L, "application/pdf", THIRTY_MIB,
			"a".repeat(64), "temporary/pdf", Instant.parse("2026-08-05T00:00:00Z"), " 안내문.pdf ");

		assertThat(asset.kind()).isEqualTo(MediaAssetKind.PDF);
		assertThat(asset.originalFileName()).isEqualTo("안내문.pdf");
	}

	@Test
	void rejects_pdf_larger_than_30_mib() {
		assertThatThrownBy(() -> MediaAsset.reserve(7L, 11L, "application/pdf", THIRTY_MIB + 1,
			"a".repeat(64), "temporary/pdf", Instant.parse("2026-08-05T00:00:00Z"), "안내문.pdf"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejects_pdf_without_a_safe_pdf_file_name() {
		assertThatThrownBy(() -> MediaAsset.reserve(7L, 11L, "application/pdf", 10,
			"a".repeat(64), "temporary/pdf", Instant.parse("2026-08-05T00:00:00Z"), "../안내문.pdf"))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
