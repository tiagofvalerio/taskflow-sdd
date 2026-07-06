package com.taskflow.domain.model;

import com.taskflow.domain.exception.TaskDeleteNotPendingException;
import com.taskflow.domain.exception.TaskStatusChangeBlockedException;
import com.taskflow.domain.exception.TaskStatusRegressionException;

import java.time.Instant;
import java.util.Objects;

/**
 * Task aggregate. References its owning project by {@link ProjectId}; status
 * transitions receive the owner's {@link ProjectStatus} as a fact because
 * rule 6 depends on it — the rule itself is decided here, not in services.
 *
 * Rule precedence inside transitions is spec-mandated: rule 6 (project
 * archived?) is checked before rule 5 (is the transition valid?).
 */
public final class Task {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 2000;

    private final TaskId id;
    private final ProjectId projectId;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private final Instant createdAt;
    private Instant completedAt;

    private Task(TaskId id, ProjectId projectId, String title, String description,
                 TaskStatus status, TaskPriority priority, Instant createdAt,
                 Instant completedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        this.title = validTitle(title);
        this.description = validDescription(description);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.priority = Objects.requireNonNull(priority, "priority must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.completedAt = completedAt;
    }

    /** Package-private: {@link Project#addTask} is the sole creation path (rule 4 lives there). */
    static Task create(ProjectId projectId, String title, String description,
                       TaskPriority priority) {
        return new Task(TaskId.newId(), projectId, title, description,
                TaskStatus.PENDING, priority, Instant.now(), null);
    }

    /** Rebuilds a persisted task as-is. For persistence adapters only — no rule checks. */
    public static Task reconstitute(TaskId id, ProjectId projectId, String title,
                                    String description, TaskStatus status,
                                    TaskPriority priority, Instant createdAt,
                                    Instant completedAt) {
        return new Task(id, projectId, title, description, status, priority,
                createdAt, completedAt);
    }

    /** pending -> in_progress. Rule 6 checked before rule 5. */
    public void startProgress(ProjectStatus ownerProjectStatus) {
        ensureStatusChangeAllowed(ownerProjectStatus);
        if (status != TaskStatus.PENDING) {
            throw new TaskStatusRegressionException(id, status, TaskStatus.IN_PROGRESS);
        }
        status = TaskStatus.IN_PROGRESS;
    }

    /**
     * in_progress -> done. Rule 6 checked before rule 5; rejecting any
     * non-in_progress source is what blocks the pending -> done skip.
     * Business rule 3: completedAt is assigned here, never accepted as input.
     */
    public void complete(ProjectStatus ownerProjectStatus) {
        ensureStatusChangeAllowed(ownerProjectStatus);
        if (status != TaskStatus.IN_PROGRESS) {
            throw new TaskStatusRegressionException(id, status, TaskStatus.DONE);
        }
        status = TaskStatus.DONE;
        completedAt = Instant.now();
    }

    /** Business rule 2: only pending tasks can be deleted. */
    public boolean canBeDeleted() {
        return status == TaskStatus.PENDING;
    }

    /** Throwing guard for rule 2 — called by the application layer before deleting. */
    public void ensureCanBeDeleted() {
        if (!canBeDeleted()) {
            throw new TaskDeleteNotPendingException(id, status);
        }
    }

    // Rule 6 blacklists only status: title, description and priority stay
    // editable even while the owning project is archived — hence no
    // ProjectStatus parameter on the three methods below.

    public void changeTitle(String title) {
        this.title = validTitle(title);
    }

    public void changeDescription(String description) {
        this.description = validDescription(description);
    }

    public void changePriority(TaskPriority priority) {
        this.priority = Objects.requireNonNull(priority, "priority must not be null");
    }

    public TaskId id() {
        return id;
    }

    public ProjectId projectId() {
        return projectId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public TaskStatus status() {
        return status;
    }

    public TaskPriority priority() {
        return priority;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    private void ensureStatusChangeAllowed(ProjectStatus ownerProjectStatus) {
        Objects.requireNonNull(ownerProjectStatus, "ownerProjectStatus must not be null");
        if (ownerProjectStatus == ProjectStatus.ARCHIVED) {
            throw new TaskStatusChangeBlockedException(id, projectId);
        }
    }

    private static String validTitle(String title) {
        Objects.requireNonNull(title, "title must not be null");
        if (title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "title must not be blank and must have at most %d characters"
                            .formatted(TITLE_MAX_LENGTH));
        }
        return title;
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
