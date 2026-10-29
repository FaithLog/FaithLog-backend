package com.faithlog.global.response;

import java.util.List;

/** Structured validation metadata. Rejected values and validator arguments are excluded. */
public record ValidationErrors(List<FieldError> fieldErrors) {
	public record FieldError(String field, String code) {}
}
