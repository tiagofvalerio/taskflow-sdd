package com.taskflow.application.usecase;

import com.taskflow.application.port.ProjectRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectStatus;

import java.util.List;
import java.util.Objects;

public final class ListProjectsUseCase {

    private final ProjectRepository projects;

    public ListProjectsUseCase(ProjectRepository projects) {
        this.projects = Objects.requireNonNull(projects);
    }

    /** statusFilter may be null (no filter). Ordering comes from the port contract. */
    public List<Project> execute(ProjectStatus statusFilter) {
        return projects.findAll(statusFilter);
    }
}
