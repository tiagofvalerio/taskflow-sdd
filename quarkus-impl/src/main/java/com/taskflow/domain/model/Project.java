package com.taskflow.domain.model;

import com.taskflow.domain.exception.ProjectArchiveBlockedException;
import com.taskflow.domain.exception.TaskCreateInArchivedProjectException;

import java.time.Instant;
import java.util.Objects;

/**
 * Project aggregate root. Tasks are a separate aggregate referencing it by
 * {@link ProjectId}; facts about them (e.g. "has a task in progress") are
 * supplied by the caller — the rules themselves live here.
 */
public final class Project {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 2000;

    private final ProjectId id;
    private String name;
    private String description;
    private ProjectStatus status;
    private final Instant createdAt;

    private Project(ProjectId id, String name, String description,
                    ProjectStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = validName(name);
        this.description = validDescription(description);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** Every new project starts active. */
    public static Project create(String name, String description) {
        return new Project(ProjectId.newId(), name, description,
                ProjectStatus.ACTIVE, Instant.now());
    }

    /** Rebuilds a persisted project as-is. For persistence adapters only — no rule checks. */
    public static Project reconstitute(ProjectId id, String name, String description,
                                       ProjectStatus status, Instant createdAt) {
        return new Project(id, name, description, status, createdAt);
    }

    /**
     * Business rule 1: archiving is blocked while any task is in progress.
     * Archiving an already-archived project is a no-op, never a violation —
     * a same-state change is not a transition.
     */
    public void archive(boolean hasTaskInProgress) {
        if (status == ProjectStatus.ARCHIVED) {
            return;
        }
        if (hasTaskInProgress) {
            throw new ProjectArchiveBlockedException(id);
        }
        status = ProjectStatus.ARCHIVED;
    }

    /** Un-archiving is allowed and unguarded; activating an active project is a no-op. */
    public void activate() {
        status = ProjectStatus.ACTIVE;
    }

    /**
     * Business rule 4: an archived project does not accept new tasks.
     * Sole creation path for tasks — every new task starts pending.
     */
    public Task addTask(String title, String description, TaskPriority priority) {
        if (status == ProjectStatus.ARCHIVED) {
            throw new TaskCreateInArchivedProjectException(id);
        }
        return Task.create(id, title, description, priority);
    }

    public void rename(String name) {
        this.name = validName(name);
    }

    public void changeDescription(String description) {
        this.description = validDescription(description);
    }

    public boolean isArchived() {
        return status == ProjectStatus.ARCHIVED;
    }

    public ProjectId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ProjectStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String validName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank() || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "name must not be blank and must have at most %d characters"
                            .formatted(NAME_MAX_LENGTH));
        }
        return name;
    }

    private static String validDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "description must have at most %d characters"
                            .formatted(DESCRIPTION_MAX_LENGTH));
        }
        return description;
    }
}
