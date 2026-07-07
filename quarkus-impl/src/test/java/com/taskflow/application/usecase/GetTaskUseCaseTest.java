package com.taskflow.application.usecase;

import com.taskflow.application.exception.TaskNotFoundException;
import com.taskflow.application.fake.InMemoryTaskRepository;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTaskUseCaseTest {

    private final InMemoryTaskRepository tasks = new InMemoryTaskRepository();
    private final GetTaskUseCase useCase = new GetTaskUseCase(tasks);

    @Test
    void returnsStoredTask() {
        Task stored = tasks.save(Task.reconstitute(TaskId.newId(), ProjectId.newId(), "t",
                null, TaskStatus.PENDING, TaskPriority.LOW,
                Instant.parse("2024-01-01T10:00:00Z"), null));
        assertEquals(stored.id(), useCase.execute(stored.id()).id());
    }

    @Test
    void unknownIdSignalsNotFound() {
        TaskId unknown = TaskId.newId();
        TaskNotFoundException ex = assertThrows(TaskNotFoundException.class,
                () -> useCase.execute(unknown));
        assertEquals(unknown, ex.taskId());
    }
}
