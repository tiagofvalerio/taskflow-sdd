package com.taskflow.application.usecase;

import com.taskflow.application.command.UpdateTaskCommand;
import com.taskflow.application.exception.TaskNotFoundException;
import com.taskflow.application.port.ProjectRepository;
import com.taskflow.application.port.TaskRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;

import java.util.Objects;

/**
 * Handles PATCH /tarefas/{id}. Atomic like updateProject: one save at the
 * end, any domain throw aborts everything. The owner project is loaded only
 * when the status actually changes — its status is the rule-6 fact the
 * domain needs; the rules themselves stay in Task.
 */
public final class UpdateTaskUseCase {

    private final TaskRepository tasks;
    private final ProjectRepository projects;

    public UpdateTaskUseCase(TaskRepository tasks, ProjectRepository projects) {
        this.tasks = Objects.requireNonNull(tasks);
        this.projects = Objects.requireNonNull(projects);
    }

    public Task execute(TaskId id, UpdateTaskCommand command) {
        Task task = tasks.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        // Same-state status is a per-field no-op (spec PATCH semantics) even
        // when the project is archived — a no-op is not a status change.
        if (command.status().isPresent() && command.status().value() != task.status()) {
            Project owner = projects.findById(task.projectId())
                    .orElseThrow(() -> new IllegalStateException(
                            "task %s references missing project %s"
                                    .formatted(id.value(), task.projectId().value())));
            task.changeStatusTo(command.status().value(), owner.status());
        }
        command.title().ifPresent(task::changeTitle);
        command.description().ifPresent(task::changeDescription);
        command.priority().ifPresent(task::changePriority);

        return tasks.save(task);
    }
}
