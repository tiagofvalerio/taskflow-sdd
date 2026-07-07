package com.taskflow.application.usecase;

import com.taskflow.application.command.PatchField;
import com.taskflow.application.command.UpdateProjectCommand;
import com.taskflow.application.exception.ProjectNotFoundException;
import com.taskflow.application.fake.InMemoryProjectRepository;
import com.taskflow.application.fake.InMemoryTaskRepository;
import com.taskflow.domain.exception.ProjectArchiveBlockedException;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateProjectUseCaseTest {

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryTaskRepository tasks = new InMemoryTaskRepository();
    private final UpdateProjectUseCase useCase = new UpdateProjectUseCase(projects, tasks);

    private Project storedProject(ProjectStatus status) {
        return projects.save(Project.reconstitute(ProjectId.newId(), "p", "old desc",
                status, Instant.parse("2024-01-01T10:00:00Z")));
    }

    private void storedTaskIn(ProjectId projectId, TaskStatus status) {
        tasks.save(Task.reconstitute(TaskId.newId(), projectId, "t", null, status,
                TaskPriority.MEDIUM, Instant.parse("2024-01-01T10:00:00Z"),
                status == TaskStatus.DONE ? Instant.parse("2024-01-02T10:00:00Z") : null));
    }

    private static UpdateProjectCommand command(PatchField<String> name,
                                                PatchField<String> description,
                                                PatchField<ProjectStatus> status) {
        return new UpdateProjectCommand(name, description, status);
    }

    @Test
    void renameAndDescriptionArePersisted() {
        Project stored = storedProject(ProjectStatus.ACTIVE);
        useCase.execute(stored.id(), command(
                PatchField.ofNullable("renamed"), PatchField.ofNullable("new desc"), PatchField.absent()));

        Project reloaded = projects.findById(stored.id()).orElseThrow();
        assertEquals("renamed", reloaded.name());
        assertEquals("new desc", reloaded.description());
    }

    @Test
    void explicitNullDescriptionClearsIt() {
        Project stored = storedProject(ProjectStatus.ACTIVE);
        useCase.execute(stored.id(), command(
                PatchField.absent(), PatchField.ofNullable(null), PatchField.absent()));

        assertNull(projects.findById(stored.id()).orElseThrow().description());
    }

    @Test
    void absentFieldsAreUntouched() {
        Project stored = storedProject(ProjectStatus.ACTIVE);
        useCase.execute(stored.id(), command(
                PatchField.ofNullable("renamed"), PatchField.absent(), PatchField.absent()));

        Project reloaded = projects.findById(stored.id()).orElseThrow();
        assertEquals("old desc", reloaded.description());
        assertEquals(ProjectStatus.ACTIVE, reloaded.status());
    }

    @Test
    void archivesWhenNoTaskInProgress() {
        Project stored = storedProject(ProjectStatus.ACTIVE);
        storedTaskIn(stored.id(), TaskStatus.PENDING);
        useCase.execute(stored.id(), command(
                PatchField.absent(), PatchField.absent(), PatchField.ofNullable(ProjectStatus.ARCHIVED)));

        assertEquals(ProjectStatus.ARCHIVED, projects.findById(stored.id()).orElseThrow().status());
    }

    @Test
    void archiveBlockedByInProgressTask() {
        Project stored = storedProject(ProjectStatus.ACTIVE);
        storedTaskIn(stored.id(), TaskStatus.IN_PROGRESS);

        assertThrows(ProjectArchiveBlockedException.class, () -> useCase.execute(stored.id(),
                command(PatchField.absent(), PatchField.absent(),
                        PatchField.ofNullable(ProjectStatus.ARCHIVED))));
        assertEquals(ProjectStatus.ACTIVE, projects.findById(stored.id()).orElseThrow().status());
    }

    @Test
    void patchIsAtomicWhenArchiveIsBlocked() {
        Project stored = storedProject(ProjectStatus.ACTIVE);
        storedTaskIn(stored.id(), TaskStatus.IN_PROGRESS);

        assertThrows(ProjectArchiveBlockedException.class, () -> useCase.execute(stored.id(),
                command(PatchField.ofNullable("renamed"), PatchField.absent(),
                        PatchField.ofNullable(ProjectStatus.ARCHIVED))));
        // Rule violation rejects the whole request — the valid name change must not land.
        assertEquals("p", projects.findById(stored.id()).orElseThrow().name());
    }

    @Test
    void patchIsAtomicWhenALaterFieldFailsAfterAValidStatusChange() {
        // Status is processed first and succeeds in memory; the invalid name
        // then throws. Nothing may land — this is the direction that actually
        // exercises the fakes' defensive copies.
        Project stored = storedProject(ProjectStatus.ACTIVE);

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(stored.id(),
                command(PatchField.ofNullable("  "), PatchField.absent(),
                        PatchField.ofNullable(ProjectStatus.ARCHIVED))));
        assertEquals(ProjectStatus.ACTIVE, projects.findById(stored.id()).orElseThrow().status());
    }

    @Test
    void sameStateStatusIsANoOpAndOtherFieldsStillApply() {
        Project stored = storedProject(ProjectStatus.ARCHIVED);
        storedTaskIn(stored.id(), TaskStatus.IN_PROGRESS);

        // status: archived on already-archived project — not a transition, no
        // rule evaluated even though an in_progress task exists; name applies.
        Project result = useCase.execute(stored.id(), command(
                PatchField.ofNullable("renamed"), PatchField.absent(),
                PatchField.ofNullable(ProjectStatus.ARCHIVED)));

        assertEquals(ProjectStatus.ARCHIVED, result.status());
        assertEquals("renamed", projects.findById(stored.id()).orElseThrow().name());
    }

    @Test
    void unarchiveIsUnguarded() {
        Project stored = storedProject(ProjectStatus.ARCHIVED);
        useCase.execute(stored.id(), command(
                PatchField.absent(), PatchField.absent(), PatchField.ofNullable(ProjectStatus.ACTIVE)));

        assertEquals(ProjectStatus.ACTIVE, projects.findById(stored.id()).orElseThrow().status());
    }

    @Test
    void unknownIdSignalsNotFound() {
        assertThrows(ProjectNotFoundException.class, () -> useCase.execute(ProjectId.newId(),
                command(PatchField.ofNullable("x"), PatchField.absent(), PatchField.absent())));
    }
}
