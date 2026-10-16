package com.faithlog.poll.controller.dto.request;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PollPriceAmountRequestTest {
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void accepts_integer_amount_and_optional_null() throws Exception {
		assertThat(mapper.readValue("{\"content\":\"라떼\",\"priceAmount\":4500}", AddPollOptionRequest.class).priceAmount()).isEqualTo(4500);
		assertThat(mapper.readValue("{\"menuId\":1}", AddPollOptionRequest.class).priceAmount()).isNull();
	}

	@Test
	void rejects_fractional_string_and_out_of_range_amounts() {
		for (String amount : new String[] {"4500.5", "\"4500\"", "2147483648"}) {
			String json = "{\"content\":\"라떼\",\"priceAmount\":" + amount + "}";
			assertThatThrownBy(() -> mapper.readValue(json, AddPollOptionRequest.class)).isInstanceOf(java.io.IOException.class);
			assertThatThrownBy(() -> mapper.readValue(json, PollOptionRequest.class)).isInstanceOf(java.io.IOException.class);
		}
	}
}
