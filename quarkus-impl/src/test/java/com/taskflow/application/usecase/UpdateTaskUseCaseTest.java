package com.taskflow.application.usecase;

import com.taskflow.application.command.PatchField;
import com.taskflow.application.command.UpdateTaskCommand;
import com.taskflow.application.exception.TaskNotFoundException;
import com.taskflow.application.fake.InMemoryProjectRepository;
import com.taskflow.application.fake.InMemoryTaskRepository;
import com.taskflow.domain.exception.TaskStatusChangeBlockedException;
import com.taskflow.domain.exception.TaskStatusRegressionException;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.ProjectStatus;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateTaskUseCaseTest {

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryTaskRepository tasks = new InMemoryTaskRepository();
    private final UpdateTaskUseCase useCase = new UpdateTaskUseCase(tasks, projects);

    private ProjectId storedProject(ProjectStatus status) {
        return projects.save(Project.reconstitute(ProjectId.newId(), "p", null, status,
                Instant.parse("2024-01-01T10:00:00Z"))).id();
    }

    private Task storedTask(ProjectId projectId, TaskStatus status) {
        return tasks.save(Task.reconstitute(TaskId.newId(), projectId, "t", "old desc", status,
                TaskPriority.MEDIUM, Instant.parse("2024-01-01T10:00:00Z"),
                status == TaskStatus.DONE ? Instant.parse("2024-01-02T10:00:00Z") : null));
    }

    private static UpdateTaskCommand command(PatchField<String> title,
                                             PatchField<String> description,
                                             PatchField<TaskStatus> status,
                                             PatchField<TaskPriority> priority) {
        return new UpdateTaskCommand(title, description, status, priority);
    }

    private static UpdateTaskCommand statusOnly(TaskStatus status) {
        return command(PatchField.absent(), PatchField.absent(),
                PatchField.ofNullable(status), PatchField.absent());
    }

    @Test
    void editsNonStatusFieldsIncludingExplicitNullDescription() {
        Task stored = storedTask(storedProject(ProjectStatus.ACTIVE), TaskStatus.PENDING);
        Task returned = useCase.execute(stored.id(), command(
                PatchField.ofNullable("new title"), PatchField.ofNullable(null),
                PatchField.absent(), PatchField.ofNullable(TaskPriority.HIGH)));

        assertEquals("new title", returned.title());
        Task reloaded = tasks.findById(stored.id()).orElseThrow();
        assertEquals("new title", reloaded.title());
        assertNull(reloaded.description());
        assertEquals(TaskPriority.HIGH, reloaded.priority());
        assertEquals(TaskStatus.PENDING, reloaded.status());
    }

    @Test
    void advancesStatusForwardAndCompleteSetsCompletedAt() {
        Task stored = storedTask(storedProject(ProjectStatus.ACTIVE), TaskStatus.PENDING);

        useCase.execute(stored.id(), statusOnly(TaskStatus.IN_PROGRESS));
        assertEquals(TaskStatus.IN_PROGRESS, tasks.findById(stored.id()).orElseThrow().status());

        useCase.execute(stored.id(), statusOnly(TaskStatus.DONE));
        Task done = tasks.findById(stored.id()).orElseThrow();
        assertEquals(TaskStatus.DONE, done.status());
        assertNotNull(done.completedAt());
    }

    @Test
    void skipAheadAndBackwardMovesAreRejected() {
        Task pending = storedTask(storedProject(ProjectStatus.ACTIVE), TaskStatus.PENDING);
        assertThrows(TaskStatusRegressionException.class,
                () -> useCase.execute(pending.id(), statusOnly(TaskStatus.DONE)));

        Task done = storedTask(storedProject(ProjectStatus.ACTIVE), TaskStatus.DONE);
        assertThrows(TaskStatusRegressionException.class,
                () -> useCase.execute(done.id(), statusOnly(TaskStatus.PENDING)));
    }

    @Test
    void statusChangeFailsFastWhenOwnerProjectIsMissing() {
        Task orphan = storedTask(ProjectId.newId(), TaskStatus.PENDING);
        assertThrows(IllegalStateException.class,
                () -> useCase.execute(orphan.id(), statusOnly(TaskStatus.IN_PROGRESS)));
    }

    @Test
    void statusChangeBlockedWhenProjectArchivedRule6WinsOverRule5() {
        // pending -> done violates rule 5 too; archived owner means rule 6's
        // error must be the one reported.
        Task stored = storedTask(storedProject(ProjectStatus.ARCHIVED), TaskStatus.PENDING);
        assertThrows(TaskStatusChangeBlockedException.class,
                () -> useCase.execute(stored.id(), statusOnly(TaskStatus.DONE)));
    }

    @Test
    void sameStateStatusUnderArchivedProjectIsANoOpAndOtherFieldsApply() {
        Task stored = storedTask(storedProject(ProjectStatus.ARCHIVED), TaskStatus.PENDING);
        useCase.execute(stored.id(), command(
                PatchField.ofNullable("new title"), PatchField.absent(),
                PatchField.ofNullable(TaskStatus.PENDING), PatchField.absent()));

        Task reloaded = tasks.findById(stored.id()).orElseThrow();
        assertEquals("new title", reloaded.title());
        assertEquals(TaskStatus.PENDING, reloaded.status());
    }

    @Test
    void nonStatusFieldsStayEditableUnderArchivedProject() {
        Task stored = storedTask(storedProject(ProjectStatus.ARCHIVED), TaskStatus.PENDING);
        useCase.execute(stored.id(), command(
                PatchField.ofNullable("edited"), PatchField.absent(),
                PatchField.absent(), PatchField.ofNullable(TaskPriority.LOW)));

        Task reloaded = tasks.findById(stored.id()).orElseThrow();
        assertEquals("edited", reloaded.title());
        assertEquals(TaskPriority.LOW, reloaded.priority());
    }

    @Test
    void patchIsAtomicWhenStatusChangeIsRejected() {
        Task stored = storedTask(storedProject(ProjectStatus.ACTIVE), TaskStatus.PENDING);

        assertThrows(TaskStatusRegressionException.class, () -> useCase.execute(stored.id(),
                command(PatchField.ofNullable("new title"), PatchField.absent(),
                        PatchField.ofNullable(TaskStatus.DONE), PatchField.absent())));
        // Whole request rejected — the valid title change must not land.
        assertEquals("t", tasks.findById(stored.id()).orElseThrow().title());
    }

    @Test
    void patchIsAtomicWhenALaterFieldFailsAfterAValidStatusChange() {
        // Status advances first in memory; the oversized title then throws.
        // Nothing may land — exercises the fakes' defensive copies for real.
        Task stored = storedTask(storedProject(ProjectStatus.ACTIVE), TaskStatus.PENDING);

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(stored.id(),
                command(PatchField.ofNullable("a".repeat(201)), PatchField.absent(),
                        PatchField.ofNullable(TaskStatus.IN_PROGRESS), PatchField.absent())));
        assertEquals(TaskStatus.PENDING, tasks.findById(stored.id()).orElseThrow().status());
    }

    @Test
    void unknownTaskSignalsNotFound() {
        assertThrows(TaskNotFoundException.class, () -> useCase.execute(TaskId.newId(),
                statusOnly(TaskStatus.IN_PROGRESS)));
    }
}
