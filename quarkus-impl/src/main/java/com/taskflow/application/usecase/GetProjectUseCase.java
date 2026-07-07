package com.taskflow.application.usecase;

import com.taskflow.application.exception.ProjectNotFoundException;
import com.taskflow.application.port.ProjectRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;

import java.util.Objects;

public final class GetProjectUseCase {

    private final ProjectRepository projects;

    public GetProjectUseCase(ProjectRepository projects) {
        this.projects = Objects.requireNonNull(projects);
    }

    public Project execute(ProjectId id) {
        return projects.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }
}
