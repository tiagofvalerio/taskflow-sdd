package com.taskflow.application.command;

import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;

import java.util.Objects;

/** Partial-update input for updateTask; each field is tri-state (see {@link PatchField}). */
public record UpdateTaskCommand(PatchField<String> title,
                                PatchField<String> description,
                                PatchField<TaskStatus> status,
                                PatchField<TaskPriority> priority) {

    public UpdateTaskCommand {
        Objects.requireNonNull(title, "title field must not be null (use PatchField.absent())");
        Objects.requireNonNull(description, "description field must not be null (use PatchField.absent())");
        Objects.requireNonNull(status, "status field must not be null (use PatchField.absent())");
        Objects.requireNonNull(priority, "priority field must not be null (use PatchField.absent())");
    }
}
