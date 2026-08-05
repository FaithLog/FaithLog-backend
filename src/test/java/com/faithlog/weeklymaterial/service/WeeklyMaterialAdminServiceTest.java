package com.faithlog.weeklymaterial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.result.WeeklyMaterialWeekResult;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

class WeeklyMaterialAdminServiceTest {
	@Test
	void managerPutAndResponseAssemblyShareOneTransactionAndManagerAuthorization() throws Exception {
		WeeklyMaterialCommandService commands = mock(WeeklyMaterialCommandService.class);
		WeeklyMaterialQueryService queries = mock(WeeklyMaterialQueryService.class);
		WeeklyMaterialAdminService service = new WeeklyMaterialAdminService(commands, queries);
		LocalDate week = LocalDate.of(2026, 8, 3);
		WeeklyMaterialWeekResult expected = new WeeklyMaterialWeekResult(week, null, null, null);
		when(queries.getWeekForManager(1L, 100L, week)).thenReturn(expected);

		assertThat(service.putAndGet(1L, week, WeeklyMaterialType.SUNDAY_SHARING_SHEET, 20L, 100L))
			.isSameAs(expected);
		var order = inOrder(commands, queries);
		order.verify(commands).put(1L, week, WeeklyMaterialType.SUNDAY_SHARING_SHEET, 20L, 100L);
		order.verify(queries).getWeekForManager(1L, 100L, week);

		Transactional transaction = AnnotatedElementUtils.findMergedAnnotation(
			WeeklyMaterialAdminService.class.getMethod("putAndGet", Long.class, LocalDate.class,
				WeeklyMaterialType.class, Long.class, Long.class), Transactional.class);
		assertThat(transaction).isNotNull();
		assertThat(transaction.readOnly()).isFalse();
	}
}
