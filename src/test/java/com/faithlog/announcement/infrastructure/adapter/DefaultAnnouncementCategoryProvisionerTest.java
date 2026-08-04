package com.faithlog.announcement.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultAnnouncementCategoryProvisionerTest {

	@Mock AnnouncementCategoryRepositoryPort categoryRepository;

	@Test
	void creates_the_approved_default_category_in_the_campus_transaction() {
		when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		new DefaultAnnouncementCategoryProvisioner(categoryRepository).afterCampusCreated(7L);

		ArgumentCaptor<AnnouncementCategory> category = ArgumentCaptor.forClass(AnnouncementCategory.class);
		verify(categoryRepository).save(category.capture());
		assertThat(category.getValue().campusId()).isEqualTo(7L);
		assertThat(category.getValue().name()).isEqualTo("일반");
		assertThat(category.getValue().color()).isEqualTo("#3B82F6");
		assertThat(category.getValue().displayOrder()).isZero();
		assertThat(category.getValue().isActive()).isTrue();
	}
}
