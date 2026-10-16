package com.faithlog.poll.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.infrastructure.repository.CoffeeMenuCatalogRepository;
import com.faithlog.poll.service.command.CreatePollOptionCommand;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoffeeManualOptionTest {
	private final PollOptionSnapshotResolver resolver = new PollOptionSnapshotResolver(mock(CoffeeMenuCatalogRepository.class));

	@Test
	void direct_poll_accepts_manual_name_and_price() {
		assertThat(resolver.resolvePollOptions(PollType.COFFEE,
			List.of(new CreatePollOptionCommand("  직접 라떼  ", null, 4500, 1))))
			.containsExactly(new PollOptionSnapshot("직접 라떼", null, 4500, 1));
	}

	@Test
	void manual_poll_rejects_missing_negative_price_or_invalid_name() {
		for (CreatePollOptionCommand option : List.of(
			new CreatePollOptionCommand("라떼", null, null, 1),
			new CreatePollOptionCommand("라떼", null, -1, 1),
			new CreatePollOptionCommand("라떼", null, 0, 1),
			new CreatePollOptionCommand(" ", null, 4500, 1),
			new CreatePollOptionCommand("가".repeat(201), null, 4500, 1))) {
			assertThatThrownBy(() -> resolver.resolvePollOptions(PollType.COFFEE, List.of(option)))
				.isInstanceOf(BusinessException.class);
		}
	}
	@Test
	void user_added_manual_coffee_option_accepts_name_and_price() {
		assertThat(resolver.resolveUserAddedOption(PollType.COFFEE, "  라떼  ", null, 4500, 3))
			.isEqualTo(new PollOptionSnapshot("라떼", null, 4500, 3));
	}

	@Test
	void user_added_manual_coffee_option_rejects_invalid_name_and_price() {
		for (Integer price : new Integer[] {null, 0, -1}) {
			assertThatThrownBy(() -> resolver.resolveUserAddedOption(PollType.COFFEE, "라떼", null, price, 1))
				.isInstanceOf(BusinessException.class);
		}
		for (String name : new String[] {null, " ", "\u0000", "가".repeat(201)}) {
			assertThatThrownBy(() -> resolver.resolveUserAddedOption(PollType.COFFEE, name, null, 4500, 1))
				.isInstanceOf(BusinessException.class);
		}
	}

	@Test
	void user_added_options_reject_mixed_catalog_and_manual_fields_and_non_coffee_prices() {
		assertThatThrownBy(() -> resolver.resolveUserAddedOption(PollType.COFFEE, "라떼", 1L, 4500, 1))
			.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> resolver.resolveUserAddedOption(PollType.COFFEE, null, 1L, 4500, 1))
			.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> resolver.resolveUserAddedOption(PollType.CUSTOM, "항목", null, 4500, 1))
			.isInstanceOf(BusinessException.class);
	}

}
