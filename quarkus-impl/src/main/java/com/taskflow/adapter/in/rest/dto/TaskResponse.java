package com.taskflow.adapter.in.rest.dto;

import com.taskflow.domain.model.Task;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Mirrors the spec's Task response schema. `completedAt` is emitted as an
 * explicit null until the task is done (nullable in the spec schema).
 */
public record TaskResponse(UUID id, String title, String description, String status,
                           String priority, Instant createdAt, Instant completedAt,
                           UUID projectId) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.id().value(),
                task.title(),
                task.description(),
                task.status().name().toLowerCase(Locale.ROOT),
                task.priority().name().toLowerCase(Locale.ROOT),
                task.createdAt(),
                task.completedAt(),
                task.projectId().value());
    }
}
