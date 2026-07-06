package com.taskflow.domain.exception;

import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskStatus;

/**
 * Business rule 5: task status is forward-only and strictly sequential
 * (pending -> in_progress -> done, one step at a time). Any backward move
 * or skip-ahead violates it.
 */
public final class TaskStatusRegressionException extends DomainRuleViolationException {

    private final TaskId taskId;
    private final TaskStatus currentStatus;
    private final TaskStatus requestedStatus;

    public TaskStatusRegressionException(TaskId taskId, TaskStatus currentStatus,
                                         TaskStatus requestedStatus) {
        super("Task %s cannot move from %s to %s: status may only advance one step at a time"
                .formatted(taskId.value(), currentStatus, requestedStatus));
        this.taskId = taskId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public TaskId taskId() {
        return taskId;
    }

    public TaskStatus currentStatus() {
        return currentStatus;
    }

    public TaskStatus requestedStatus() {
        return requestedStatus;
    }
}
