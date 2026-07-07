package com.taskflow.application.port;

import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.ProjectStatus;

import java.util.List;
import java.util.Optional;

/** Outbound port. Speaks domain types only; implemented by a persistence adapter. */
public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(ProjectId id);

    /**
     * All projects matching the optional status filter (null = no filter),
     * ordered by createdAt ascending, id ascending as tie-breaker — the
     * ordering is part of the API contract, so it is part of this port's
     * contract too, not left to the adapter's database defaults.
     */
    List<Project> findAll(ProjectStatus statusFilter);
}
