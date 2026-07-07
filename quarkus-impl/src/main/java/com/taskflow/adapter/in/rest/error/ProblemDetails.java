package com.taskflow.adapter.in.rest.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/** RFC 7807 body. `instance` omitted when null (it is optional in the spec schema). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetails(String type, String title, int status, String detail,
                             String instance) {

    public static final String MEDIA_TYPE = "application/problem+json";
}
