package com.taskflow.adapter.in.rest.validation;

/**
 * Path `id` is not a valid UUID. `reportAsFieldError` selects the spec's two
 * response shapes: POST/PATCH endpoints report it inside `errors[]`
 * (ValidationProblemDetails), GET/DELETE as a plain ProblemDetails.
 */
public final class InvalidPathParamException extends RuntimeException {

    private final boolean reportAsFieldError;

    public InvalidPathParamException(boolean reportAsFieldError) {
        super("path id is not a valid UUID");
        this.reportAsFieldError = reportAsFieldError;
    }

    public boolean reportAsFieldError() {
        return reportAsFieldError;
    }
}
