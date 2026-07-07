package com.taskflow.application.command;

import com.taskflow.domain.model.TaskPriority;

/** Input for createTask. Status is never accepted on create — every task starts pending. */
public record CreateTaskCommand(String title, String description, TaskPriority priority) {
}
