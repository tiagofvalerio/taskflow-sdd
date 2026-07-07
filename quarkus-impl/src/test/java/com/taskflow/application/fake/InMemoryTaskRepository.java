package com.taskflow.application.fake;

import com.taskflow.application.port.TaskRepository;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Same defensive-copy discipline as {@link InMemoryProjectRepository}. */
public final class InMemoryTaskRepository implements TaskRepository {

    private static final Comparator<Task> CONTRACT_ORDER =
            Comparator.comparing(Task::createdAt)
                    .thenComparing(task -> task.id().value());

    private final Map<TaskId, Task> store = new HashMap<>();

    @Override
    public Task save(Task task) {
        store.put(task.id(), copy(task));
        return copy(task);
    }

    @Override
    public Optional<Task> findById(TaskId id) {
        return Optional.ofNullable(store.get(id)).map(InMemoryTaskRepository::copy);
    }

    @Override
    public List<Task> findByProjectId(ProjectId projectId, TaskStatus statusFilter,
                                      TaskPriority priorityFilter) {
        return store.values().stream()
                .filter(task -> task.projectId().equals(projectId))
                .filter(task -> statusFilter == null || task.status() == statusFilter)
                .filter(task -> priorityFilter == null || task.priority() == priorityFilter)
                .sorted(CONTRACT_ORDER)
                .map(InMemoryTaskRepository::copy)
                .toList();
    }

    @Override
    public void delete(TaskId id) {
        store.remove(id);
    }

    @Override
    public boolean existsByProjectIdAndStatus(ProjectId projectId, TaskStatus status) {
        return store.values().stream()
                .anyMatch(task -> task.projectId().equals(projectId) && task.status() == status);
    }

    private static Task copy(Task task) {
        return Task.reconstitute(task.id(), task.projectId(), task.title(), task.description(),
                task.status(), task.priority(), task.createdAt(), task.completedAt());
    }
}
