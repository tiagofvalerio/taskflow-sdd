package com.taskflow.application.usecase;

import com.taskflow.application.fake.InMemoryProjectRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListProjectsUseCaseTest {

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final ListProjectsUseCase useCase = new ListProjectsUseCase(projects);

    private Project seed(String uuid, String name, ProjectStatus status, String createdAt) {
        return projects.save(Project.reconstitute(
                new ProjectId(UUID.fromString(uuid)), name, null, status,
                Instant.parse(createdAt)));
    }

    @Test
    void ordersByCreatedAtAscThenIdAsc() {
        // Same createdAt for b/c: id is the tie-breaker.
        seed("00000000-0000-0000-0000-000000000002", "c", ProjectStatus.ACTIVE, "2024-01-02T10:00:00Z");
        seed("00000000-0000-0000-0000-000000000003", "a", ProjectStatus.ACTIVE, "2024-01-01T10:00:00Z");
        seed("00000000-0000-0000-0000-000000000001", "b", ProjectStatus.ACTIVE, "2024-01-02T10:00:00Z");

        List<String> names = useCase.execute(null).stream().map(Project::name).toList();
        assertEquals(List.of("a", "b", "c"), names);
    }

    @Test
    void filtersByStatus() {
        seed("00000000-0000-0000-0000-000000000001", "active1", ProjectStatus.ACTIVE, "2024-01-01T10:00:00Z");
        seed("00000000-0000-0000-0000-000000000002", "archived1", ProjectStatus.ARCHIVED, "2024-01-02T10:00:00Z");

        List<Project> archived = useCase.execute(ProjectStatus.ARCHIVED);
        assertEquals(1, archived.size());
        assertEquals("archived1", archived.getFirst().name());
    }

    @Test
    void nullFilterReturnsAllAndEmptyStoreReturnsEmptyList() {
        assertTrue(useCase.execute(null).isEmpty());
        seed("00000000-0000-0000-0000-000000000001", "p", ProjectStatus.ACTIVE, "2024-01-01T10:00:00Z");
        assertEquals(1, useCase.execute(null).size());
    }
}
