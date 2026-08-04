package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.service.command.CreateAnnouncementCategoryCommand;
import com.faithlog.announcement.service.command.UpdateAnnouncementCategoryCommand;
import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.service.port.CampusRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnnouncementCategoryCommandServiceTest {

	@Mock
	private AnnouncementCategoryRepositoryPort categoryRepository;
	@Mock
	private CampusRepositoryPort campusRepository;
	@Mock
	private AnnouncementAccessPolicy accessPolicy;

	private AnnouncementCategoryCommandService service;

	@BeforeEach
	void setUp() {
		service = new AnnouncementCategoryCommandService(categoryRepository, campusRepository, accessPolicy);
	}

	@Test
	void create_checks_manager_campus_and_case_insensitive_duplicate() {
		Campus campus = Campus.create("캠퍼스", "서울", null, "ABC123");
		when(campusRepository.findById(1L)).thenReturn(Optional.of(campus));
		when(categoryRepository.existsByCampusIdAndNameIgnoreCase(1L, "예배 안내")).thenReturn(false);
		when(categoryRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
			.thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.createCategory(new CreateAnnouncementCategoryCommand(
			1L, 10L, "  예배 안내  ", "#3b82f6", 2
		));

		verify(accessPolicy).requireManager(1L, 10L);
		assertThat(result.name()).isEqualTo("예배 안내");
		assertThat(result.color()).isEqualTo("#3B82F6");
		assertThat(result.displayOrder()).isEqualTo(2);
	}

	@Test
	void create_maps_concurrent_database_duplicate_to_typed_conflict() {
		when(campusRepository.findById(1L)).thenReturn(Optional.of(Campus.create("캠퍼스", null, null, "ABC123")));
		when(categoryRepository.existsByCampusIdAndNameIgnoreCase(1L, "일반")).thenReturn(false);
		when(categoryRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
			.thenThrow(new DataIntegrityViolationException("concurrent duplicate"));

		assertThatThrownBy(() -> service.createCategory(new CreateAnnouncementCategoryCommand(
			1L, 10L, "일반", "#ABCDEF", 0)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_CATEGORY_DUPLICATE));
	}

	@Test
	void create_rejects_logically_duplicate_name() {
		when(campusRepository.findById(1L)).thenReturn(Optional.of(Campus.create("캠퍼스", null, null, "ABC123")));
		when(categoryRepository.existsByCampusIdAndNameIgnoreCase(1L, "일반")).thenReturn(true);

		assertThatThrownBy(() -> service.createCategory(new CreateAnnouncementCategoryCommand(
			1L, 10L, " 일반 ", "#ABCDEF", 0
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_CATEGORY_DUPLICATE));
	}

	@Test
	void update_rejects_other_campus_id_and_deactivate_is_soft() {
		AnnouncementCategory category = AnnouncementCategory.create(2L, "일반", "#ABCDEF", 0);
		when(categoryRepository.findByCampusIdAndIdForUpdate(1L, 5L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateCategory(new UpdateAnnouncementCategoryCommand(
			1L, 5L, 10L, "긴급", "#FF0000", 1
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANNOUNCEMENT_CATEGORY_NOT_FOUND));

		when(categoryRepository.findByCampusIdAndIdForUpdate(2L, 5L)).thenReturn(Optional.of(category));
		service.deactivateCategory(2L, 5L, 10L);

		assertThat(category.isActive()).isFalse();
		verify(accessPolicy).requireManager(2L, 10L);
	}
}
