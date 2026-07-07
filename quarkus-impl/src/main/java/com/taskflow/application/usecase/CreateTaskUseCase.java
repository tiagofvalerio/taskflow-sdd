package com.taskflow.application.usecase;

import com.taskflow.application.command.CreateTaskCommand;
import com.taskflow.application.exception.ProjectNotFoundException;
import com.taskflow.application.port.ProjectRepository;
import com.taskflow.application.port.TaskRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.Task;

import java.util.Objects;

public final class CreateTaskUseCase {

    private final ProjectRepository projects;
    private final TaskRepository tasks;

    public CreateTaskUseCase(ProjectRepository projects, TaskRepository tasks) {
        this.projects = Objects.requireNonNull(projects);
        this.tasks = Objects.requireNonNull(tasks);
    }

    public Task execute(ProjectId projectId, CreateTaskCommand command) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        // Rule 4 lives in the domain: addTask throws if the project is archived.
        Task task = project.addTask(command.title(), command.description(), command.priority());
        return tasks.save(task);
    }
}
