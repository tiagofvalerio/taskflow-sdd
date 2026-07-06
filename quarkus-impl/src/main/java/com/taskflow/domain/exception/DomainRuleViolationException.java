package com.taskflow.domain.exception;

/**
 * Base type for every business-rule violation (spec rules 1, 2, 4, 5, 6 —
 * mapped to HTTP 422 by the REST adapter). Sealed so the adapter's exception
 * mapper can switch exhaustively over the rule types. Rule 3 (completedAt as
 * input) has no exception here: it is a malformed-request concern (400)
 * rejected by schema validation before the domain is ever reached.
 */
public abstract sealed class DomainRuleViolationException extends RuntimeException
        permits ProjectArchiveBlockedException,
                TaskDeleteNotPendingException,
                TaskCreateInArchivedProjectException,
                TaskStatusRegressionException,
                TaskStatusChangeBlockedException {

    protected DomainRuleViolationException(String message) {
        super(message);
    }
}
