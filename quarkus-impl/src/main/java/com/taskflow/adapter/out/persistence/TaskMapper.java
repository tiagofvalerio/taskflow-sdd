package com.taskflow.adapter.out.persistence;

import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;

import java.util.Locale;

/** TaskId/ProjectId and the enums wrap/unwrap at this boundary and nowhere else. */
final class TaskMapper {

    private TaskMapper() {
    }

    static Task toDomain(TaskEntity entity) {
        return Task.reconstitute(
                new TaskId(entity.id),
                new ProjectId(entity.projectId),
                entity.title,
                entity.description,
                TaskStatus.valueOf(entity.status.toUpperCase(Locale.ROOT)),
                TaskPriority.valueOf(entity.priority.toUpperCase(Locale.ROOT)),
                entity.createdAt,
                entity.completedAt);
    }

    static TaskEntity toEntity(Task task) {
        TaskEntity entity = new TaskEntity();
        entity.id = task.id().value();
        entity.projectId = task.projectId().value();
        entity.title = task.title();
        entity.description = task.description();
        entity.status = task.status().name().toLowerCase(Locale.ROOT);
        entity.priority = task.priority().name().toLowerCase(Locale.ROOT);
        entity.createdAt = task.createdAt();
        entity.completedAt = task.completedAt();
        return entity;
    }

    static String wireValue(TaskStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    static String wireValue(TaskPriority priority) {
        return priority.name().toLowerCase(Locale.ROOT);
    }
}
