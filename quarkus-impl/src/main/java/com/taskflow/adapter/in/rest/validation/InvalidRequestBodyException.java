package com.taskflow.adapter.in.rest.validation;

import com.taskflow.adapter.in.rest.error.FieldError;

import java.util.List;

/** Malformed request body; carries every violating field at once (spec policy). */
public final class InvalidRequestBodyException extends RuntimeException {

    private final String detail;
    private final List<FieldError> errors;

    public InvalidRequestBodyException(String detail, List<FieldError> errors) {
        super(detail);
        this.detail = detail;
        this.errors = List.copyOf(errors);
    }

    public String detail() {
        return detail;
    }

    public List<FieldError> errors() {
        return errors;
    }
}
