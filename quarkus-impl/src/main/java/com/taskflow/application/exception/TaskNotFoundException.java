package com.taskflow.application.exception;

import com.taskflow.domain.model.TaskId;

/** Signals a 404 for tasks; mapped to the resource-not-found problem by the REST adapter. */
public final class TaskNotFoundException extends RuntimeException {

    private final TaskId taskId;

    public TaskNotFoundException(TaskId taskId) {
        super("No task found with id %s".formatted(taskId.value()));
        this.taskId = taskId;
    }

    public TaskId taskId() {
        return taskId;
    }
}
