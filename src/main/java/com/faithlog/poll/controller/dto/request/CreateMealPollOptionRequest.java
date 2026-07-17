package com.faithlog.poll.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;

public class CreateMealPollOptionRequest {

	@NotBlank
	@Size(max = 200)
	@JsonProperty
	private String content;

	@JsonProperty
	private int sortOrder;

	private final Set<String> unknownFields = new LinkedHashSet<>();

	public CreateMealPollOptionRequest() {
	}

	@JsonAnySetter
	public void unknownField(String name, Object value) {
		unknownFields.add(name);
	}

	public String content() {
		return content;
	}

	public int sortOrder() {
		return sortOrder;
	}

	public Set<String> unknownFields() {
		return Set.copyOf(unknownFields);
	}
}
