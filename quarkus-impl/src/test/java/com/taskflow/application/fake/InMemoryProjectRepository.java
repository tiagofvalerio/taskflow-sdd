package com.taskflow.application.fake;

import com.taskflow.application.port.ProjectRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.ProjectStatus;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores and returns defensive copies (via reconstitute) so mutations on a
 * loaded entity never reach the store without an explicit save — otherwise
 * the PATCH-atomicity tests would pass vacuously.
 */
public final class InMemoryProjectRepository implements ProjectRepository {

    // Tie-break compares the canonical hex string, matching Postgres's
    // unsigned-byte uuid order — UUID.compareTo is signed and can disagree.
    private static final Comparator<Project> CONTRACT_ORDER =
            Comparator.comparing(Project::createdAt)
                    .thenComparing(project -> project.id().value().toString());

    private final Map<ProjectId, Project> store = new HashMap<>();

    @Override
    public Project save(Project project) {
        store.put(project.id(), copy(project));
        return copy(project);
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        return Optional.ofNullable(store.get(id)).map(InMemoryProjectRepository::copy);
    }

    @Override
    public List<Project> findAll(ProjectStatus statusFilter) {
        return store.values().stream()
                .filter(project -> statusFilter == null || project.status() == statusFilter)
                .sorted(CONTRACT_ORDER)
                .map(InMemoryProjectRepository::copy)
                .toList();
    }

    private static Project copy(Project project) {
        return Project.reconstitute(project.id(), project.name(), project.description(),
                project.status(), project.createdAt());
    }
}
