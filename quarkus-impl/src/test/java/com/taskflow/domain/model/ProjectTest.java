package com.taskflow.domain.model;

import com.taskflow.domain.exception.ProjectArchiveBlockedException;
import com.taskflow.domain.exception.TaskCreateInArchivedProjectException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTest {

    @Nested
    class Creation {

        @Test
        void newProjectStartsActiveWithIdAndCreatedAt() {
            Instant before = Instant.now();
            Project project = Project.create("Taskflow", "challenge project");

            assertNotNull(project.id());
            assertEquals(ProjectStatus.ACTIVE, project.status());
            assertEquals("Taskflow", project.name());
            assertEquals("challenge project", project.description());
            assertFalse(project.createdAt().isBefore(before));
            assertFalse(project.createdAt().isAfter(Instant.now()));
        }

        @Test
        void descriptionIsOptional() {
            assertNull(Project.create("Taskflow", null).description());
        }

        @Test
        void rejectsBlankName() {
            assertThrows(IllegalArgumentException.class, () -> Project.create("  ", null));
        }

        @Test
        void rejectsNameOver100Characters() {
            String tooLong = "a".repeat(101);
            assertThrows(IllegalArgumentException.class, () -> Project.create(tooLong, null));
            assertDoesNotThrow(() -> Project.create("a".repeat(100), null));
        }

        @Test
        void rejectsDescriptionOver2000Characters() {
            String tooLong = "a".repeat(2001);
            assertThrows(IllegalArgumentException.class, () -> Project.create("p", tooLong));
            assertDoesNotThrow(() -> Project.create("p", "a".repeat(2000)));
        }
    }

    @Nested
    @DisplayName("Rule 1 — archive blocked by in_progress task")
    class Archiving {

        @Test
        void archivesWhenNoTaskInProgress() {
            Project project = Project.create("p", null);
            project.archive(false);
            assertEquals(ProjectStatus.ARCHIVED, project.status());
            assertTrue(project.isArchived());
        }

        @Test
        void archiveBlockedWhenATaskIsInProgress() {
            Project project = Project.create("p", null);
            ProjectArchiveBlockedException ex = assertThrows(
                    ProjectArchiveBlockedException.class, () -> project.archive(true));
            assertEquals(project.id(), ex.projectId());
            assertEquals(ProjectStatus.ACTIVE, project.status());
        }

        @Test
        void archivingAnArchivedProjectIsANoOpNeverAViolation() {
            Project project = Project.create("p", null);
            project.archive(false);
            // Same-state change is not a transition: must not throw even if
            // the in-progress fact is (impossibly) true.
            assertDoesNotThrow(() -> project.archive(true));
            assertEquals(ProjectStatus.ARCHIVED, project.status());
        }

        @Test
        void activateReversesArchivingUnguarded() {
            Project project = Project.create("p", null);
            project.archive(false);
            project.activate();
            assertEquals(ProjectStatus.ACTIVE, project.status());
            assertDoesNotThrow(project::activate);
        }
    }

    @Nested
    @DisplayName("Rule 4 — no new tasks in archived projects")
    class AddingTasks {

        @Test
        void addTaskOnActiveProjectReturnsPendingTaskOwnedByIt() {
            Project project = Project.create("p", null);
            Task task = project.addTask("write domain", "pure java", TaskPriority.HIGH);

            assertNotNull(task.id());
            assertEquals(project.id(), task.projectId());
            assertEquals(TaskStatus.PENDING, task.status());
            assertEquals(TaskPriority.HIGH, task.priority());
            assertNull(task.completedAt());
        }

        @Test
        void addTaskOnArchivedProjectIsRejected() {
            Project project = Project.create("p", null);
            project.archive(false);
            TaskCreateInArchivedProjectException ex = assertThrows(
                    TaskCreateInArchivedProjectException.class,
                    () -> project.addTask("t", null, TaskPriority.LOW));
            assertEquals(project.id(), ex.projectId());
        }
    }

    @Nested
    class Editing {

        @Test
        void renameAndChangeDescriptionWorkEvenWhileArchived() {
            Project project = Project.create("p", null);
            project.archive(false);

            project.rename("renamed");
            project.changeDescription("new description");

            assertEquals("renamed", project.name());
            assertEquals("new description", project.description());
        }

        @Test
        void renameRejectsInvalidName() {
            Project project = Project.create("p", null);
            assertThrows(IllegalArgumentException.class, () -> project.rename(" "));
            assertThrows(IllegalArgumentException.class, () -> project.rename("a".repeat(101)));
            assertEquals("p", project.name());
        }
    }

    @Test
    void reconstituteRestoresStateWithoutRuleChecks() {
        ProjectId id = ProjectId.newId();
        Instant createdAt = Instant.parse("2024-01-01T10:00:00Z");
        Project project = Project.reconstitute(id, "p", null,
                ProjectStatus.ARCHIVED, createdAt);

        assertEquals(id, project.id());
        assertEquals(ProjectStatus.ARCHIVED, project.status());
        assertEquals(createdAt, project.createdAt());
    }
}
