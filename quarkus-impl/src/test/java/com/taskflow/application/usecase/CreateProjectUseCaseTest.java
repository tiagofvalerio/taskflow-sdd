package com.taskflow.application.usecase;

import com.taskflow.application.command.CreateProjectCommand;
import com.taskflow.application.fake.InMemoryProjectRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateProjectUseCaseTest {

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final CreateProjectUseCase useCase = new CreateProjectUseCase(projects);

    @Test
    void createsActiveProjectAndPersistsIt() {
        Project created = useCase.execute(new CreateProjectCommand("Taskflow", "desc"));

        assertNotNull(created.id());
        assertNotNull(created.createdAt());
        assertEquals(ProjectStatus.ACTIVE, created.status());
        assertEquals("Taskflow", created.name());
        assertEquals("desc", created.description());
        assertTrue(projects.findById(created.id()).isPresent());
    }
}
