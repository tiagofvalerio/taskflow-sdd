package com.taskflow.application.usecase;

import com.taskflow.application.exception.TaskNotFoundException;
import com.taskflow.application.port.TaskRepository;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;

import java.util.Objects;

public final class GetTaskUseCase {

    private final TaskRepository tasks;

    public GetTaskUseCase(TaskRepository tasks) {
        this.tasks = Objects.requireNonNull(tasks);
    }

    public Task execute(TaskId id) {
        return tasks.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
