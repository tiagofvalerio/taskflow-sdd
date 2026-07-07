package com.taskflow.application.usecase;

import com.taskflow.application.exception.ProjectNotFoundException;
import com.taskflow.application.fake.InMemoryProjectRepository;
import com.taskflow.application.fake.InMemoryTaskRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.ProjectStatus;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListTasksUseCaseTest {

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryTaskRepository tasks = new InMemoryTaskRepository();
    private final ListTasksUseCase useCase = new ListTasksUseCase(projects, tasks);

    private ProjectId projectId;

    @BeforeEach
    void seedProject() {
        Project project = projects.save(Project.reconstitute(ProjectId.newId(), "p", null,
                ProjectStatus.ACTIVE, Instant.parse("2024-01-01T10:00:00Z")));
        projectId = project.id();
    }

    private void seedTask(String uuid, String title, TaskStatus status, TaskPriority priority,
                          String createdAt) {
        tasks.save(Task.reconstitute(new TaskId(UUID.fromString(uuid)), projectId, title, null,
                status, priority, Instant.parse(createdAt),
                status == TaskStatus.DONE ? Instant.parse("2024-02-01T10:00:00Z") : null));
    }

    @Test
    void unknownProjectSignalsNotFoundBeforeAnyFiltering() {
        assertThrows(ProjectNotFoundException.class,
                () -> useCase.execute(ProjectId.newId(), null, null));
    }

    @Test
    void returnsOnlyThatProjectsTasksOrderedByCreatedAtThenId() {
        seedTask("00000000-0000-0000-0000-000000000002", "second", TaskStatus.PENDING,
                TaskPriority.LOW, "2024-01-01T10:00:00Z");
        seedTask("00000000-0000-0000-0000-000000000001", "first", TaskStatus.PENDING,
                TaskPriority.LOW, "2024-01-01T10:00:00Z");
        // Another project's task must not appear.
        tasks.save(Task.reconstitute(TaskId.newId(), ProjectId.newId(), "other", null,
                TaskStatus.PENDING, TaskPriority.LOW, Instant.parse("2024-01-01T09:00:00Z"), null));

        List<String> titles = useCase.execute(projectId, null, null)
                .stream().map(Task::title).toList();
        assertEquals(List.of("first", "second"), titles);
    }

    @Test
    void filtersByStatusByPriorityAndByBothWithAndSemantics() {
        seedTask("00000000-0000-0000-0000-000000000001", "pending-low", TaskStatus.PENDING,
                TaskPriority.LOW, "2024-01-01T10:00:00Z");
        seedTask("00000000-0000-0000-0000-000000000002", "pending-high", TaskStatus.PENDING,
                TaskPriority.HIGH, "2024-01-02T10:00:00Z");
        seedTask("00000000-0000-0000-0000-000000000003", "done-high", TaskStatus.DONE,
                TaskPriority.HIGH, "2024-01-03T10:00:00Z");

        assertEquals(2, useCase.execute(projectId, TaskStatus.PENDING, null).size());
        assertEquals(2, useCase.execute(projectId, null, TaskPriority.HIGH).size());

        List<Task> both = useCase.execute(projectId, TaskStatus.PENDING, TaskPriority.HIGH);
        assertEquals(1, both.size());
        assertEquals("pending-high", both.getFirst().title());
    }
}
