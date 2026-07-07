package com.taskflow.application.exception;

import com.taskflow.domain.model.ProjectId;

/** Signals a 404 for projects; mapped to the resource-not-found problem by the REST adapter. */
public final class ProjectNotFoundException extends RuntimeException {

    private final ProjectId projectId;

    public ProjectNotFoundException(ProjectId projectId) {
        super("No project found with id %s".formatted(projectId.value()));
        this.projectId = projectId;
    }

    public ProjectId projectId() {
        return projectId;
    }
}
