package com.faithlog.weeklymaterial.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.weeklymaterial.controller.dto.request.PutWeeklyMaterialRequest;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.WeeklyMaterialCommandService;
import com.faithlog.weeklymaterial.service.WeeklyMaterialQueryService;
import com.faithlog.weeklymaterial.service.WeeklyMaterialAdminService;
import com.faithlog.weeklymaterial.service.result.WeeklyMaterialFileResult;
import com.faithlog.weeklymaterial.service.result.WeeklyMaterialWeekResult;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WeeklyMaterialControllerTest {
	private final WeeklyMaterialCommandService commands = mock(WeeklyMaterialCommandService.class);
	private final WeeklyMaterialQueryService queries = mock(WeeklyMaterialQueryService.class);
	private final WeeklyMaterialAdminService admin = mock(WeeklyMaterialAdminService.class);
	private final AuthenticatedUser user = new AuthenticatedUser(100L, "USER", "session", "jti", Instant.MAX);

	@Test
	void adminPutMapsBodyOnlyMediaAssetIdAndReturnsDtoWithoutObjectKey() {
		var controller = new AdminWeeklyMaterialController(admin, commands);
		LocalDate week = LocalDate.of(2026, 8, 3);
		var result = new WeeklyMaterialWeekResult(week, null,
			new WeeklyMaterialFileResult(20L, WeeklyMaterialType.SUNDAY_SHARING_SHEET, "sheet.pdf",
				200L, "a".repeat(64), Instant.parse("2026-08-03T00:00:00Z")), null);
		when(admin.putAndGet(1L, week, WeeklyMaterialType.SUNDAY_SHARING_SHEET, 20L, 100L)).thenReturn(result);

		var response = controller.put(user, 1L, week, WeeklyMaterialType.SUNDAY_SHARING_SHEET,
			new PutWeeklyMaterialRequest(20L));

		verify(admin).putAndGet(1L, week, WeeklyMaterialType.SUNDAY_SHARING_SHEET, 20L, 100L);
		assertThat(response.getBody().data().sundaySharingSheet().assetId()).isEqualTo(20L);
		assertThat(response.getBody().data().sundaySharingSheet().getClass().getRecordComponents())
			.extracting(component -> component.getName())
			.doesNotContain("objectKey", "url");
	}

	@Test
	void adminDeleteReturns204AndDelegatesOnlyOneSlot() {
		var controller = new AdminWeeklyMaterialController(admin, commands);
		LocalDate week = LocalDate.of(2026, 8, 3);

		var response = controller.delete(user, 1L, week, WeeklyMaterialType.SHEPHERD_GUIDE);

		assertThat(response.getStatusCode().value()).isEqualTo(204);
		verify(commands).delete(1L, week, WeeklyMaterialType.SHEPHERD_GUIDE, 100L);
	}
}
