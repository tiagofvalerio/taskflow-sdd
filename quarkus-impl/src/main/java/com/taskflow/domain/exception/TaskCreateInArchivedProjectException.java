package com.taskflow.domain.exception;

import com.taskflow.domain.model.ProjectId;

/** Business rule 4: an archived project does not accept new tasks. */
public final class TaskCreateInArchivedProjectException extends DomainRuleViolationException {

    private final ProjectId projectId;

    public TaskCreateInArchivedProjectException(ProjectId projectId) {
        super("Project %s is archived and does not accept new tasks"
                .formatted(projectId.value()));
        this.projectId = projectId;
    }

    public ProjectId projectId() {
        return projectId;
    }
}
