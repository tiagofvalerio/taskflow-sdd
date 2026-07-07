package com.taskflow.adapter.in.rest.validation;

/** One invalid query parameter — fail-fast, only the first is ever reported (spec). */
public final class InvalidQueryParamException extends RuntimeException {

    private final String detail;

    public InvalidQueryParamException(String detail) {
        super(detail);
        this.detail = detail;
    }

    public String detail() {
        return detail;
    }
}
