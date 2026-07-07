package com.taskflow.application.usecase;

import com.taskflow.application.exception.TaskNotFoundException;
import com.taskflow.application.port.TaskRepository;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;

import java.util.Objects;

public final class DeleteTaskUseCase {

    private final TaskRepository tasks;

    public DeleteTaskUseCase(TaskRepository tasks) {
        this.tasks = Objects.requireNonNull(tasks);
    }

    public void execute(TaskId id) {
        Task task = tasks.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        // Rule 2 lives in the domain.
        task.ensureCanBeDeleted();
        tasks.delete(id);
    }
}
