package com.taskflow.application.usecase;

import com.taskflow.application.exception.TaskNotFoundException;
import com.taskflow.application.fake.InMemoryTaskRepository;
import com.taskflow.domain.exception.TaskDeleteNotPendingException;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeleteTaskUseCaseTest {

    private final InMemoryTaskRepository tasks = new InMemoryTaskRepository();
    private final DeleteTaskUseCase useCase = new DeleteTaskUseCase(tasks);

    private Task storedTask(TaskStatus status) {
        return tasks.save(Task.reconstitute(TaskId.newId(), ProjectId.newId(), "t", null,
                status, TaskPriority.MEDIUM, Instant.parse("2024-01-01T10:00:00Z"),
                status == TaskStatus.DONE ? Instant.parse("2024-01-02T10:00:00Z") : null));
    }

    @Test
    void deletesPendingTask() {
        Task stored = storedTask(TaskStatus.PENDING);
        useCase.execute(stored.id());
        assertTrue(tasks.findById(stored.id()).isEmpty());
    }

    @Test
    void nonPendingTasksCannotBeDeletedAndStayStored() {
        Task inProgress = storedTask(TaskStatus.IN_PROGRESS);
        assertThrows(TaskDeleteNotPendingException.class, () -> useCase.execute(inProgress.id()));
        assertTrue(tasks.findById(inProgress.id()).isPresent());

        Task done = storedTask(TaskStatus.DONE);
        assertThrows(TaskDeleteNotPendingException.class, () -> useCase.execute(done.id()));
        assertTrue(tasks.findById(done.id()).isPresent());
    }

    @Test
    void unknownIdSignalsNotFound() {
        assertThrows(TaskNotFoundException.class, () -> useCase.execute(TaskId.newId()));
    }
}
