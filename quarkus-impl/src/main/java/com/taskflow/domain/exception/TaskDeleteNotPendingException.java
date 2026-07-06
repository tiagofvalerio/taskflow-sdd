package com.taskflow.domain.exception;

import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskStatus;

/** Business rule 2: only tasks with status pending can be deleted. */
public final class TaskDeleteNotPendingException extends DomainRuleViolationException {

    private final TaskId taskId;
    private final TaskStatus currentStatus;

    public TaskDeleteNotPendingException(TaskId taskId, TaskStatus currentStatus) {
        super("Task %s cannot be deleted: status is %s, only pending tasks can be deleted"
                .formatted(taskId.value(), currentStatus));
        this.taskId = taskId;
        this.currentStatus = currentStatus;
    }

    public TaskId taskId() {
        return taskId;
    }

    public TaskStatus currentStatus() {
        return currentStatus;
    }
}
