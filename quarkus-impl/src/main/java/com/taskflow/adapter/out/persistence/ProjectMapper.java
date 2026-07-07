package com.taskflow.adapter.out.persistence;

import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.ProjectStatus;

import java.util.Locale;

/** ProjectId and the status enum wrap/unwrap at this boundary and nowhere else. */
final class ProjectMapper {

    private ProjectMapper() {
    }

    static Project toDomain(ProjectEntity entity) {
        return Project.reconstitute(
                new ProjectId(entity.id),
                entity.name,
                entity.description,
                ProjectStatus.valueOf(entity.status.toUpperCase(Locale.ROOT)),
                entity.createdAt);
    }

    static ProjectEntity toEntity(Project project) {
        ProjectEntity entity = new ProjectEntity();
        entity.id = project.id().value();
        entity.name = project.name();
        entity.description = project.description();
        entity.status = project.status().name().toLowerCase(Locale.ROOT);
        entity.createdAt = project.createdAt();
        return entity;
    }
}
