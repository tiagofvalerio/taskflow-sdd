package com.taskflow.application.usecase;

import com.taskflow.application.exception.ProjectNotFoundException;
import com.taskflow.application.fake.InMemoryProjectRepository;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetProjectUseCaseTest {

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final GetProjectUseCase useCase = new GetProjectUseCase(projects);

    @Test
    void returnsStoredProject() {
        Project stored = projects.save(Project.create("p", null));
        assertEquals(stored.id(), useCase.execute(stored.id()).id());
    }

    @Test
    void unknownIdSignalsNotFound() {
        ProjectId unknown = ProjectId.newId();
        ProjectNotFoundException ex = assertThrows(ProjectNotFoundException.class,
                () -> useCase.execute(unknown));
        assertEquals(unknown, ex.projectId());
    }
}
