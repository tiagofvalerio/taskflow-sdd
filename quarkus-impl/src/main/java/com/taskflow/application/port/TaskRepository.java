package com.taskflow.application.port;

import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;

import java.util.List;
import java.util.Optional;

/** Outbound port. Speaks domain types only; implemented by a persistence adapter. */
public interface TaskRepository {

    Task save(Task task);

    Optional<Task> findById(TaskId id);

    /**
     * Tasks of the given project matching the optional filters (null = no
     * filter; both given = AND semantics), ordered by createdAt ascending,
     * id ascending as tie-breaker — contract ordering, same as
     * {@link ProjectRepository#findAll}.
     */
    List<Task> findByProjectId(ProjectId projectId, TaskStatus statusFilter,
                               TaskPriority priorityFilter);

    void delete(TaskId id);

    /** The business-rule-1 fact: does the project have any task in the given status? */
    boolean existsByProjectIdAndStatus(ProjectId projectId, TaskStatus status);
}
