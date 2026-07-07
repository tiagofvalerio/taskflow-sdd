package com.taskflow.adapter.out.persistence;

import com.taskflow.application.port.ProjectRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.ProjectStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ProjectPersistenceTest {

    @Inject
    ProjectRepository projects;

    @Inject
    EntityManager em;

    private Project seed(String uuid, String name, ProjectStatus status, String createdAt) {
        return projects.save(Project.reconstitute(new ProjectId(UUID.fromString(uuid)),
                name, null, status, Instant.parse(createdAt)));
    }

    @Test
    @TestTransaction
    void roundTripPreservesAllFields() {
        Project original = Project.reconstitute(ProjectId.newId(), "projeto", "descrição",
                ProjectStatus.ARCHIVED, Instant.parse("2024-01-01T10:00:00Z"));
        projects.save(original);

        Project reloaded = projects.findById(original.id()).orElseThrow();
        assertEquals(original.id(), reloaded.id());
        assertEquals("projeto", reloaded.name());
        assertEquals("descrição", reloaded.description());
        assertEquals(ProjectStatus.ARCHIVED, reloaded.status());
        assertEquals(original.createdAt(), reloaded.createdAt());
    }

    @Test
    @TestTransaction
    void nullDescriptionSurvivesRoundTrip() {
        Project original = seed("00000000-0000-0000-0000-000000000001", "p",
                ProjectStatus.ACTIVE, "2024-01-01T10:00:00Z");
        assertNull(projects.findById(original.id()).orElseThrow().description());
    }

    @Test
    @TestTransaction
    void saveUpdatesExistingRow() {
        Project project = seed("00000000-0000-0000-0000-000000000001", "before",
                ProjectStatus.ACTIVE, "2024-01-01T10:00:00Z");
        project.rename("after");
        project.archive(false);
        projects.save(project);

        Project reloaded = projects.findById(project.id()).orElseThrow();
        assertEquals("after", reloaded.name());
        assertEquals(ProjectStatus.ARCHIVED, reloaded.status());
    }

    @Test
    @TestTransaction
    void findAllOrdersByCreatedAtAscThenIdAsc() {
        seed("00000000-0000-0000-0000-000000000002", "c", ProjectStatus.ACTIVE, "2024-01-02T10:00:00Z");
        seed("00000000-0000-0000-0000-000000000003", "a", ProjectStatus.ACTIVE, "2024-01-01T10:00:00Z");
        // Same createdAt as "c": id must break the tie.
        seed("00000000-0000-0000-0000-000000000001", "b", ProjectStatus.ACTIVE, "2024-01-02T10:00:00Z");

        List<String> names = projects.findAll(null).stream().map(Project::name).toList();
        assertEquals(List.of("a", "b", "c"), names);
    }

    @Test
    @TestTransaction
    void findAllFiltersByStatus() {
        seed("00000000-0000-0000-0000-000000000001", "active1", ProjectStatus.ACTIVE, "2024-01-01T10:00:00Z");
        seed("00000000-0000-0000-0000-000000000002", "archived1", ProjectStatus.ARCHIVED, "2024-01-02T10:00:00Z");

        List<Project> archived = projects.findAll(ProjectStatus.ARCHIVED);
        assertEquals(1, archived.size());
        assertEquals("archived1", archived.getFirst().name());
    }

    @Test
    @TestTransaction
    void databaseRejectsInvalidStatus() {
        assertThrows(PersistenceException.class, () -> em.createNativeQuery(
                "insert into project (id, name, status, created_at) "
                        + "values (gen_random_uuid(), 'p', 'deleted', now())")
                .executeUpdate());
    }
}
