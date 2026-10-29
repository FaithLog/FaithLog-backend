package com.faithlog.global.exception;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

@ExtendWith(RestDocumentationExtension.class)
class ValidationErrorResponseTest {

	private MockMvc mvc;

	@BeforeEach
	void setup(RestDocumentationContextProvider documentation) {
		mvc = MockMvcBuilders.standaloneSetup(new InputController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.apply(documentationConfiguration(documentation)).build();
	}

	@Test
	void returns_all_field_constraints_without_rejected_values() throws Exception {
		mvc.perform(post("/validation-example").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"private-invalid-email\",\"accountNumber\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("GLOBAL_VALIDATION_FAILED"))
			.andExpect(jsonPath("$.data.fieldErrors.length()").value(2))
			.andExpect(jsonPath("$.data.fieldErrors[0].field").value("accountNumber"))
			.andExpect(jsonPath("$.data.fieldErrors[0].code").value("NotBlank"))
			.andExpect(jsonPath("$.data.fieldErrors[1].field").value("email"))
			.andExpect(jsonPath("$.data.fieldErrors[1].code").value("Email"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-invalid-email"))))
			.andExpect(jsonPath("$.data.fieldErrors[0].rejectedValue").doesNotExist())
			.andDo(document("validation-field-errors", responseFields(
				fieldWithPath("success").description("요청 성공 여부"),
				fieldWithPath("code").description("기존 검증 오류 코드"),
				fieldWithPath("message").description("기존 호환용 검증 안내"),
				fieldWithPath("data.fieldErrors[].field").description("검증에 실패한 요청 필드 경로"),
				fieldWithPath("data.fieldErrors[].code").description("검증 제약 코드. 입력값은 제공하지 않음"),
				fieldWithPath("timestamp").description("응답 시각")
			)));
	}

	@Test
	void invalid_json_keeps_existing_contract() throws Exception {
		mvc.perform(post("/validation-example").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("GLOBAL_INVALID_JSON"))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@RestController
	static class InputController {
		@PostMapping("/validation-example")
		public void submit(@Valid @RequestBody Input input) {}
	}

	record Input(@Email String email, @NotBlank String accountNumber) {}
}
