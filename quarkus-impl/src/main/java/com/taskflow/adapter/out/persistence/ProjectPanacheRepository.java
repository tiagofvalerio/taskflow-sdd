package com.taskflow.adapter.out.persistence;

import com.taskflow.application.port.ProjectRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.ProjectStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProjectPanacheRepository
        implements ProjectRepository, PanacheRepositoryBase<ProjectEntity, UUID> {

    // Spec ordering guarantee: createdAt ASC, id ASC as tie-breaker.
    private static final Sort CONTRACT_ORDER = Sort.by("createdAt").and("id");

    @Override
    public Project save(Project project) {
        ProjectEntity merged = getEntityManager().merge(ProjectMapper.toEntity(project));
        return ProjectMapper.toDomain(merged);
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        return findByIdOptional(id.value()).map(ProjectMapper::toDomain);
    }

    @Override
    public List<Project> findAll(ProjectStatus statusFilter) {
        var entities = statusFilter == null
                ? listAll(CONTRACT_ORDER)
                : list("status = ?1", CONTRACT_ORDER,
                        statusFilter.name().toLowerCase(Locale.ROOT));
        return entities.stream().map(ProjectMapper::toDomain).toList();
    }
}
