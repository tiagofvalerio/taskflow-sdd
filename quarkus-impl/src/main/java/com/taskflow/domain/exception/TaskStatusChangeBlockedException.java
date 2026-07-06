package com.taskflow.domain.exception;

import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.TaskId;

/**
 * Business rule 6: a task belonging to an archived project cannot change
 * status (title, description and priority remain editable). Checked before
 * rule 5 — spec-mandated precedence.
 */
public final class TaskStatusChangeBlockedException extends DomainRuleViolationException {

    private final TaskId taskId;
    private final ProjectId projectId;

    public TaskStatusChangeBlockedException(TaskId taskId, ProjectId projectId) {
        super("Task %s cannot change status: its project %s is archived"
                .formatted(taskId.value(), projectId.value()));
        this.taskId = taskId;
        this.projectId = projectId;
    }

    public TaskId taskId() {
        return taskId;
    }

    public ProjectId projectId() {
        return projectId;
    }
}
