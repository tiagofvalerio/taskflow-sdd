package com.taskflow.domain.exception;

import com.taskflow.domain.model.ProjectId;

/** Business rule 1: a project cannot be archived while it has a task in progress. */
public final class ProjectArchiveBlockedException extends DomainRuleViolationException {

    private final ProjectId projectId;

    public ProjectArchiveBlockedException(ProjectId projectId) {
        super("Project %s cannot be archived: it has at least one task in progress"
                .formatted(projectId.value()));
        this.projectId = projectId;
    }

    public ProjectId projectId() {
        return projectId;
    }
}
