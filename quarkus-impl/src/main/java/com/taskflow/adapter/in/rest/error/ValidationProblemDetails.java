package com.taskflow.adapter.in.rest.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** RFC 7807 body extended with the spec's per-field error list. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationProblemDetails(String type, String title, int status, String detail,
                                       String instance, List<FieldError> errors) {
}
