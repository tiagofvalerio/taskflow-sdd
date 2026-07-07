package com.taskflow.adapter.out.persistence;

import com.taskflow.application.port.TaskRepository;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskId;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TaskPanacheRepository
        implements TaskRepository, PanacheRepositoryBase<TaskEntity, UUID> {

    // Spec ordering guarantee: createdAt ASC, id ASC as tie-breaker.
    private static final Sort CONTRACT_ORDER = Sort.by("createdAt").and("id");

    @Override
    public Task save(Task task) {
        TaskEntity merged = getEntityManager().merge(TaskMapper.toEntity(task));
        return TaskMapper.toDomain(merged);
    }

    @Override
    public Optional<Task> findById(TaskId id) {
        return findByIdOptional(id.value()).map(TaskMapper::toDomain);
    }

    @Override
    public List<Task> findByProjectId(ProjectId projectId, TaskStatus statusFilter,
                                      TaskPriority priorityFilter) {
        List<String> clauses = new ArrayList<>();
        Parameters params = Parameters.with("projectId", projectId.value());
        clauses.add("projectId = :projectId");
        if (statusFilter != null) {
            clauses.add("status = :status");
            params = params.and("status", TaskMapper.wireValue(statusFilter));
        }
        if (priorityFilter != null) {
            clauses.add("priority = :priority");
            params = params.and("priority", TaskMapper.wireValue(priorityFilter));
        }
        return list(String.join(" and ", clauses), CONTRACT_ORDER, params).stream()
                .map(TaskMapper::toDomain)
                .toList();
    }

    @Override
    public void delete(TaskId id) {
        deleteById(id.value());
    }

    @Override
    public boolean existsByProjectIdAndStatus(ProjectId projectId, TaskStatus status) {
        return count("projectId = ?1 and status = ?2",
                projectId.value(), TaskMapper.wireValue(status)) > 0;
    }
}
