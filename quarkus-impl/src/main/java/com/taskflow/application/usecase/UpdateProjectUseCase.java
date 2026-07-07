package com.taskflow.application.usecase;

import com.taskflow.application.command.UpdateProjectCommand;
import com.taskflow.application.exception.ProjectNotFoundException;
import com.taskflow.application.port.ProjectRepository;
import com.taskflow.application.port.TaskRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.TaskStatus;

import java.util.Objects;

/**
 * Handles PATCH /projetos/{id}, archive included — the spec has no dedicated
 * archive operation, status is just one of three patchable fields. PATCH is
 * atomic: everything mutates the loaded entity in memory and a single save
 * runs at the end, so any rule violation aborts the whole request.
 */
public final class UpdateProjectUseCase {

    private final ProjectRepository projects;
    private final TaskRepository tasks;

    public UpdateProjectUseCase(ProjectRepository projects, TaskRepository tasks) {
        this.projects = Objects.requireNonNull(projects);
        this.tasks = Objects.requireNonNull(tasks);
    }

    public Project execute(ProjectId id, UpdateProjectCommand command) {
        Project project = projects.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));

        // Same-state status is a per-field no-op (spec PATCH semantics, not a
        // business rule) — only an actual change reaches the domain.
        if (command.status().isPresent() && command.status().value() != project.status()) {
            switch (command.status().value()) {
                case ARCHIVED -> project.archive(
                        tasks.existsByProjectIdAndStatus(id, TaskStatus.IN_PROGRESS));
                case ACTIVE -> project.activate();
            }
        }
        command.name().ifPresent(project::rename);
        command.description().ifPresent(project::changeDescription);

        return projects.save(project);
    }
}
