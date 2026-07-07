package com.taskflow.application.usecase;

import com.taskflow.application.command.CreateTaskCommand;
import com.taskflow.application.exception.ProjectNotFoundException;
import com.taskflow.application.fake.InMemoryProjectRepository;
import com.taskflow.application.fake.InMemoryTaskRepository;
import com.taskflow.domain.exception.TaskCreateInArchivedProjectException;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.ProjectStatus;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateTaskUseCaseTest {

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryTaskRepository tasks = new InMemoryTaskRepository();
    private final CreateTaskUseCase useCase = new CreateTaskUseCase(projects, tasks);

    private Project storedProject(ProjectStatus status) {
        return projects.save(Project.reconstitute(ProjectId.newId(), "p", null,
                status, Instant.parse("2024-01-01T10:00:00Z")));
    }

    @Test
    void createsPendingTaskInActiveProject() {
        Project project = storedProject(ProjectStatus.ACTIVE);
        Task created = useCase.execute(project.id(),
                new CreateTaskCommand("write adapters", null, TaskPriority.HIGH));

        assertEquals(project.id(), created.projectId());
        assertEquals(TaskStatus.PENDING, created.status());
        assertTrue(tasks.findById(created.id()).isPresent());
    }

    @Test
    void archivedProjectRejectsNewTasksAndNothingIsSaved() {
        Project project = storedProject(ProjectStatus.ARCHIVED);

        assertThrows(TaskCreateInArchivedProjectException.class, () -> useCase.execute(
                project.id(), new CreateTaskCommand("t", null, TaskPriority.LOW)));
        assertTrue(tasks.findByProjectId(project.id(), null, null).isEmpty());
    }

    @Test
    void unknownProjectSignalsNotFound() {
        assertThrows(ProjectNotFoundException.class, () -> useCase.execute(
                ProjectId.newId(), new CreateTaskCommand("t", null, TaskPriority.LOW)));
    }
}
