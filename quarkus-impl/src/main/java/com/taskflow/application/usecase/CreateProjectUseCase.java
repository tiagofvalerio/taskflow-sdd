package com.taskflow.application.usecase;

import com.taskflow.application.command.CreateProjectCommand;
import com.taskflow.application.port.ProjectRepository;
import com.taskflow.domain.model.Project;

import java.util.Objects;

public final class CreateProjectUseCase {

    private final ProjectRepository projects;

    public CreateProjectUseCase(ProjectRepository projects) {
        this.projects = Objects.requireNonNull(projects);
    }

    public Project execute(CreateProjectCommand command) {
        return projects.save(Project.create(command.name(), command.description()));
    }
}
