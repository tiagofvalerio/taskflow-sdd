package com.taskflow.adapter.out.persistence;

import com.taskflow.application.port.ProjectRepository;
import com.taskflow.application.port.TaskRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.ProjectStatus;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TaskPersistenceTest {

    @Inject
    ProjectRepository projects;

    @Inject
    TaskRepository tasks;

    @Inject
    EntityManager em;

    private ProjectId seedProject() {
        return projects.save(Project.reconstitute(ProjectId.newId(), "p", null,
                ProjectStatus.ACTIVE, Instant.parse("2024-01-01T10:00:00Z"))).id();
    }

    private Task seedTask(ProjectId projectId, String uuid, String title, TaskStatus status,
                          TaskPriority priority, String createdAt) {
        return tasks.save(Task.reconstitute(new TaskId(UUID.fromString(uuid)), projectId,
                title, null, status, priority, Instant.parse(createdAt),
                status == TaskStatus.DONE ? Instant.parse("2024-02-01T10:00:00Z") : null));
    }

    @Test
    @TestTransaction
    void roundTripPreservesAllFieldsIncludingCompletedAt() {
        ProjectId projectId = seedProject();
        Task original = Task.reconstitute(TaskId.newId(), projectId, "tarefa", "descrição",
                TaskStatus.DONE, TaskPriority.HIGH,
                Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-02T10:00:00Z"));
        tasks.save(original);

        Task reloaded = tasks.findById(original.id()).orElseThrow();
        assertEquals(original.id(), reloaded.id());
        assertEquals(projectId, reloaded.projectId());
        assertEquals("tarefa", reloaded.title());
        assertEquals("descrição", reloaded.description());
        assertEquals(TaskStatus.DONE, reloaded.status());
        assertEquals(TaskPriority.HIGH, reloaded.priority());
        assertEquals(original.createdAt(), reloaded.createdAt());
        assertEquals(original.completedAt(), reloaded.completedAt());
    }

    @Test
    @TestTransaction
    void findByProjectIdOrdersByCreatedAtAscThenIdAsc() {
        ProjectId projectId = seedProject();
        seedTask(projectId, "00000000-0000-0000-0000-000000000002", "c", TaskStatus.PENDING,
                TaskPriority.LOW, "2024-01-02T10:00:00Z");
        seedTask(projectId, "00000000-0000-0000-0000-000000000003", "a", TaskStatus.PENDING,
                TaskPriority.LOW, "2024-01-01T10:00:00Z");
        seedTask(projectId, "00000000-0000-0000-0000-000000000001", "b", TaskStatus.PENDING,
                TaskPriority.LOW, "2024-01-02T10:00:00Z");
        // Task of another project must not appear.
        seedTask(seedProjectWithId("00000000-0000-0000-0000-00000000aaaa"),
                "00000000-0000-0000-0000-000000000004", "other", TaskStatus.PENDING,
                TaskPriority.LOW, "2024-01-01T09:00:00Z");

        List<String> titles = tasks.findByProjectId(projectId, null, null)
                .stream().map(Task::title).toList();
        assertEquals(List.of("a", "b", "c"), titles);
    }

    private ProjectId seedProjectWithId(String uuid) {
        return projects.save(Project.reconstitute(new ProjectId(UUID.fromString(uuid)), "p2",
                null, ProjectStatus.ACTIVE, Instant.parse("2024-01-01T10:00:00Z"))).id();
    }

    @Test
    @TestTransaction
    void filtersByStatusAndPriorityWithAndSemantics() {
        ProjectId projectId = seedProject();
        seedTask(projectId, "00000000-0000-0000-0000-000000000001", "pending-low",
                TaskStatus.PENDING, TaskPriority.LOW, "2024-01-01T10:00:00Z");
        seedTask(projectId, "00000000-0000-0000-0000-000000000002", "pending-high",
                TaskStatus.PENDING, TaskPriority.HIGH, "2024-01-02T10:00:00Z");
        seedTask(projectId, "00000000-0000-0000-0000-000000000003", "done-high",
                TaskStatus.DONE, TaskPriority.HIGH, "2024-01-03T10:00:00Z");

        assertEquals(2, tasks.findByProjectId(projectId, TaskStatus.PENDING, null).size());
        assertEquals(2, tasks.findByProjectId(projectId, null, TaskPriority.HIGH).size());

        List<Task> both = tasks.findByProjectId(projectId, TaskStatus.PENDING, TaskPriority.HIGH);
        assertEquals(1, both.size());
        assertEquals("pending-high", both.getFirst().title());
    }

    @Test
    @TestTransaction
    void existsByProjectIdAndStatusReportsTheRule1Fact() {
        ProjectId projectId = seedProject();
        seedTask(projectId, "00000000-0000-0000-0000-000000000001", "t",
                TaskStatus.IN_PROGRESS, TaskPriority.LOW, "2024-01-01T10:00:00Z");

        assertTrue(tasks.existsByProjectIdAndStatus(projectId, TaskStatus.IN_PROGRESS));
        assertFalse(tasks.existsByProjectIdAndStatus(projectId, TaskStatus.DONE));
    }

    @Test
    @TestTransaction
    void deleteRemovesRow() {
        ProjectId projectId = seedProject();
        Task task = seedTask(projectId, "00000000-0000-0000-0000-000000000001", "t",
                TaskStatus.PENDING, TaskPriority.LOW, "2024-01-01T10:00:00Z");
        tasks.delete(task.id());
        assertTrue(tasks.findById(task.id()).isEmpty());
    }

    @Test
    @TestTransaction
    void databaseRejectsDoneWithoutCompletedAt() {
        ProjectId projectId = seedProject();
        assertThrows(PersistenceException.class, () -> em.createNativeQuery(
                "insert into task (id, project_id, title, status, priority, created_at) "
                        + "values (gen_random_uuid(), '" + projectId.value()
                        + "', 't', 'done', 'low', now())")
                .executeUpdate());
    }
}
