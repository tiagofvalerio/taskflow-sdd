package com.taskflow.adapter.in.rest.dto;

import com.taskflow.domain.model.Project;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Mirrors the spec's Project response schema. Instant fields serialize as
 * RFC 3339 UTC with a literal Z suffix (contract-tested).
 */
public record ProjectResponse(UUID id, String name, String description, String status,
                              Instant createdAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.id().value(),
                project.name(),
                project.description(),
                project.status().name().toLowerCase(Locale.ROOT),
                project.createdAt());
    }
}
