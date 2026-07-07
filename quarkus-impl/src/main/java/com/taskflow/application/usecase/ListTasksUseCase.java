package com.taskflow.application.usecase;

import com.taskflow.application.exception.ProjectNotFoundException;
import com.taskflow.application.port.ProjectRepository;
import com.taskflow.application.port.TaskRepository;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;

import java.util.List;
import java.util.Objects;

public final class ListTasksUseCase {

    private final ProjectRepository projects;
    private final TaskRepository tasks;

    public ListTasksUseCase(ProjectRepository projects, TaskRepository tasks) {
        this.projects = Objects.requireNonNull(projects);
        this.tasks = Objects.requireNonNull(tasks);
    }

    /** Filters may be null; both given = AND. Project existence checked first (404 per spec). */
    public List<Task> execute(ProjectId projectId, TaskStatus statusFilter,
                              TaskPriority priorityFilter) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ProjectNotFoundException(projectId);
        }
        return tasks.findByProjectId(projectId, statusFilter, priorityFilter);
    }
}
