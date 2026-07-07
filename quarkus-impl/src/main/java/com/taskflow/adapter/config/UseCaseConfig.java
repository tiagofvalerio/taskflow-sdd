package com.taskflow.adapter.config;

import com.taskflow.application.port.ProjectRepository;
import com.taskflow.application.port.TaskRepository;
import com.taskflow.application.usecase.CreateProjectUseCase;
import com.taskflow.application.usecase.CreateTaskUseCase;
import com.taskflow.application.usecase.DeleteTaskUseCase;
import com.taskflow.application.usecase.GetProjectUseCase;
import com.taskflow.application.usecase.GetTaskUseCase;
import com.taskflow.application.usecase.ListProjectsUseCase;
import com.taskflow.application.usecase.ListTasksUseCase;
import com.taskflow.application.usecase.UpdateProjectUseCase;
import com.taskflow.application.usecase.UpdateTaskUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI wiring for the framework-free use cases — the application layer has no
 * annotations by design (ArchUnit-enforced), so the adapter ring produces the
 * beans, injecting the Panache adapters through the ports.
 */
@ApplicationScoped
public class UseCaseConfig {

    @Produces
    @ApplicationScoped
    CreateProjectUseCase createProject(ProjectRepository projects) {
        return new CreateProjectUseCase(projects);
    }

    @Produces
    @ApplicationScoped
    GetProjectUseCase getProject(ProjectRepository projects) {
        return new GetProjectUseCase(projects);
    }

    @Produces
    @ApplicationScoped
    ListProjectsUseCase listProjects(ProjectRepository projects) {
        return new ListProjectsUseCase(projects);
    }

    @Produces
    @ApplicationScoped
    UpdateProjectUseCase updateProject(ProjectRepository projects, TaskRepository tasks) {
        return new UpdateProjectUseCase(projects, tasks);
    }

    @Produces
    @ApplicationScoped
    CreateTaskUseCase createTask(ProjectRepository projects, TaskRepository tasks) {
        return new CreateTaskUseCase(projects, tasks);
    }

    @Produces
    @ApplicationScoped
    GetTaskUseCase getTask(TaskRepository tasks) {
        return new GetTaskUseCase(tasks);
    }

    @Produces
    @ApplicationScoped
    ListTasksUseCase listTasks(ProjectRepository projects, TaskRepository tasks) {
        return new ListTasksUseCase(projects, tasks);
    }

    @Produces
    @ApplicationScoped
    UpdateTaskUseCase updateTask(ProjectRepository projects, TaskRepository tasks) {
        return new UpdateTaskUseCase(tasks, projects);
    }

    @Produces
    @ApplicationScoped
    DeleteTaskUseCase deleteTask(TaskRepository tasks) {
        return new DeleteTaskUseCase(tasks);
    }
}
