package com.faithlog.announcement.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnnouncementCategoryTest {

	@Test
	void create_trims_name_and_normalizes_uppercase_hex_color() {
		AnnouncementCategory category = AnnouncementCategory.create(1L, "  예배 안내  ", "#3b82f6", 2);

		assertThat(category.campusId()).isEqualTo(1L);
		assertThat(category.name()).isEqualTo("예배 안내");
		assertThat(category.color()).isEqualTo("#3B82F6");
		assertThat(category.displayOrder()).isEqualTo(2);
		assertThat(category.isActive()).isTrue();
	}

	@Test
	void create_accepts_exact_name_boundary_and_rejects_invalid_values() {
		assertThat(AnnouncementCategory.create(1L, "가".repeat(30), "#ABCDEF", 0).name())
			.hasSize(30);

		assertThatThrownBy(() -> AnnouncementCategory.create(1L, " ", "#ABCDEF", 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AnnouncementCategory.create(1L, "가".repeat(31), "#ABCDEF", 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AnnouncementCategory.create(1L, "일반", "3B82F6", 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AnnouncementCategory.create(1L, "일반", "#GGGGGG", 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AnnouncementCategory.create(1L, "일반", "#ABCDEF", -1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void update_and_deactivate_preserve_category_identity() {
		AnnouncementCategory category = AnnouncementCategory.create(1L, "일반", "#ABCDEF", 0);

		category.update("  긴급  ", "#ff0000", 5);
		category.deactivate();

		assertThat(category.campusId()).isEqualTo(1L);
		assertThat(category.name()).isEqualTo("긴급");
		assertThat(category.color()).isEqualTo("#FF0000");
		assertThat(category.displayOrder()).isEqualTo(5);
		assertThat(category.isActive()).isFalse();
	}
}
