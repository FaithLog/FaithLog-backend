package com.faithlog.poll.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.service.command.CreateMealPollCommand;
import com.faithlog.poll.service.command.CreateMealPollOptionCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CreateMealPollRequest {

	@NotBlank
	@Size(max = 200)
	@JsonProperty
	private String title;

	@Size(max = 5_000)
	@JsonProperty
	private String notice;

	@JsonProperty
	private boolean isAnonymous;

	@JsonProperty
	private boolean allowUserOptionAdd;

	@NotNull
	@JsonProperty
	private Instant endsAt;

	@NotNull
	@Size(min = 1)
	@Valid
	@JsonProperty
	private List<@NotNull CreateMealPollOptionRequest> options;

	@JsonProperty
	private List<@Positive Long> imageAssetIds;

	private final Set<String> unknownFields = new LinkedHashSet<>();

	public CreateMealPollRequest() {
	}

	@JsonAnySetter
	public void unknownField(String name, Object value) {
		unknownFields.add(name);
	}

	public CreateMealPollCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new CreateMealPollCommand(
			campusId,
			authenticatedUser.userId(),
			title,
			notice == null || notice.isBlank() ? null : notice.trim(),
			isAnonymous,
			allowUserOptionAdd,
			endsAt,
			options.stream().map(option -> new CreateMealPollOptionCommand(
				option.content(), option.sortOrder(), option.unknownFields()
			)).toList(),
			Set.copyOf(unknownFields),
			imageAssetIds == null ? List.of() : imageAssetIds
		);
	}
}
