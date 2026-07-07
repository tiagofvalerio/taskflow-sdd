package com.taskflow.domain.model;

import com.taskflow.domain.exception.TaskDeleteNotPendingException;
import com.taskflow.domain.exception.TaskStatusChangeBlockedException;
import com.taskflow.domain.exception.TaskStatusRegressionException;
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

class TaskTest {

    private static Task pendingTask() {
        return Project.create("p", null).addTask("t", null, TaskPriority.MEDIUM);
    }

    private static Task inProgressTask() {
        Task task = pendingTask();
        task.startProgress(ProjectStatus.ACTIVE);
        return task;
    }

    private static Task doneTask() {
        Task task = inProgressTask();
        task.complete(ProjectStatus.ACTIVE);
        return task;
    }

    @Nested
    @DisplayName("Rule 5 — forward-only, strictly sequential transitions")
    class Transitions {

        @Test
        void happyPathAdvancesOneStepAtATime() {
            Task task = pendingTask();
            assertEquals(TaskStatus.PENDING, task.status());

            task.startProgress(ProjectStatus.ACTIVE);
            assertEquals(TaskStatus.IN_PROGRESS, task.status());

            task.complete(ProjectStatus.ACTIVE);
            assertEquals(TaskStatus.DONE, task.status());
        }

        @Test
        void skipAheadPendingToDoneIsRejected() {
            Task task = pendingTask();
            TaskStatusRegressionException ex = assertThrows(
                    TaskStatusRegressionException.class,
                    () -> task.complete(ProjectStatus.ACTIVE));
            assertEquals(TaskStatus.PENDING, ex.currentStatus());
            assertEquals(TaskStatus.DONE, ex.requestedStatus());
            assertEquals(TaskStatus.PENDING, task.status());
            assertNull(task.completedAt());
        }

        @Test
        void startProgressOnInProgressTaskIsRejected() {
            Task task = inProgressTask();
            assertThrows(TaskStatusRegressionException.class,
                    () -> task.startProgress(ProjectStatus.ACTIVE));
            assertEquals(TaskStatus.IN_PROGRESS, task.status());
        }

        @Test
        void doneIsTerminalNoBackwardMove() {
            Task task = doneTask();
            assertThrows(TaskStatusRegressionException.class,
                    () -> task.startProgress(ProjectStatus.ACTIVE));
            assertThrows(TaskStatusRegressionException.class,
                    () -> task.complete(ProjectStatus.ACTIVE));
            assertEquals(TaskStatus.DONE, task.status());
        }
    }

    @Nested
    @DisplayName("Rule 3 — completedAt set internally by complete()")
    class CompletedAt {

        @Test
        void completeAssignsCompletedAtInternally() {
            Task task = inProgressTask();
            assertNull(task.completedAt());

            Instant before = Instant.now();
            task.complete(ProjectStatus.ACTIVE);

            assertNotNull(task.completedAt());
            assertFalse(task.completedAt().isBefore(before));
            assertFalse(task.completedAt().isAfter(Instant.now()));
        }
    }

    @Nested
    @DisplayName("Rule 6 — status frozen while project archived, checked before rule 5")
    class ArchivedProjectGuard {

        @Test
        void startProgressBlockedWhenProjectArchived() {
            Task task = pendingTask();
            TaskStatusChangeBlockedException ex = assertThrows(
                    TaskStatusChangeBlockedException.class,
                    () -> task.startProgress(ProjectStatus.ARCHIVED));
            assertEquals(task.id(), ex.taskId());
            assertEquals(task.projectId(), ex.projectId());
            assertEquals(TaskStatus.PENDING, task.status());
        }

        @Test
        void rule6WinsWhenRule5IsAlsoViolated() {
            // pending -> done violates rule 5 AND, with an archived owner,
            // rule 6. Spec precedence: rule 6's error, never rule 5's.
            Task task = pendingTask();
            assertThrows(TaskStatusChangeBlockedException.class,
                    () -> task.complete(ProjectStatus.ARCHIVED));
            assertNull(task.completedAt());
        }

        @Test
        void doneTaskStatusChangeUnderArchivedProjectAlsoReturnsRule6() {
            Task task = doneTask();
            assertThrows(TaskStatusChangeBlockedException.class,
                    () -> task.startProgress(ProjectStatus.ARCHIVED));
        }

        @Test
        void nonStatusFieldsStayEditableUnderArchivedProject() {
            // No ProjectStatus parameter exists on these methods — rule 6
            // blacklists only status.
            Task task = pendingTask();
            task.changeTitle("new title");
            task.changeDescription("new description");
            task.changePriority(TaskPriority.HIGH);

            assertEquals("new title", task.title());
            assertEquals("new description", task.description());
            assertEquals(TaskPriority.HIGH, task.priority());
        }
    }

    @Nested
    @DisplayName("changeStatusTo — requested-status dispatch, rule 6 before rule 5")
    class ChangeStatusTo {

        @Test
        void dispatchesForwardTransitions() {
            Task task = pendingTask();
            task.changeStatusTo(TaskStatus.IN_PROGRESS, ProjectStatus.ACTIVE);
            assertEquals(TaskStatus.IN_PROGRESS, task.status());

            task.changeStatusTo(TaskStatus.DONE, ProjectStatus.ACTIVE);
            assertEquals(TaskStatus.DONE, task.status());
            assertNotNull(task.completedAt());
        }

        @Test
        void pendingTargetIsAlwaysARegression() {
            TaskStatusRegressionException fromInProgress = assertThrows(
                    TaskStatusRegressionException.class,
                    () -> inProgressTask().changeStatusTo(TaskStatus.PENDING, ProjectStatus.ACTIVE));
            assertEquals(TaskStatus.PENDING, fromInProgress.requestedStatus());

            assertThrows(TaskStatusRegressionException.class,
                    () -> doneTask().changeStatusTo(TaskStatus.PENDING, ProjectStatus.ACTIVE));
        }

        @Test
        void skipAheadIsARegression() {
            assertThrows(TaskStatusRegressionException.class,
                    () -> pendingTask().changeStatusTo(TaskStatus.DONE, ProjectStatus.ACTIVE));
        }

        @Test
        void rule6WinsEvenForThePendingTargetThatHasNoTransitionMethod() {
            Task task = inProgressTask();
            assertThrows(TaskStatusChangeBlockedException.class,
                    () -> task.changeStatusTo(TaskStatus.PENDING, ProjectStatus.ARCHIVED));
            assertEquals(TaskStatus.IN_PROGRESS, task.status());
        }
    }

    @Nested
    @DisplayName("Rule 2 — only pending tasks can be deleted")
    class Deletion {

        @Test
        void onlyPendingTaskCanBeDeleted() {
            assertTrue(pendingTask().canBeDeleted());
            assertFalse(inProgressTask().canBeDeleted());
            assertFalse(doneTask().canBeDeleted());
        }

        @Test
        void ensureCanBeDeletedThrowsForNonPending() {
            assertDoesNotThrow(() -> pendingTask().ensureCanBeDeleted());

            Task inProgress = inProgressTask();
            TaskDeleteNotPendingException ex = assertThrows(
                    TaskDeleteNotPendingException.class, inProgress::ensureCanBeDeleted);
            assertEquals(inProgress.id(), ex.taskId());
            assertEquals(TaskStatus.IN_PROGRESS, ex.currentStatus());

            assertThrows(TaskDeleteNotPendingException.class,
                    () -> doneTask().ensureCanBeDeleted());
        }
    }

    @Nested
    class Editing {

        @Test
        void changeTitleRejectsInvalidTitle() {
            Task task = pendingTask();
            assertThrows(IllegalArgumentException.class, () -> task.changeTitle(" "));
            assertThrows(IllegalArgumentException.class,
                    () -> task.changeTitle("a".repeat(201)));
            assertDoesNotThrow(() -> task.changeTitle("a".repeat(200)));
        }

        @Test
        void changeDescriptionRejectsOversizedAndAcceptsNull() {
            Task task = pendingTask();
            assertThrows(IllegalArgumentException.class,
                    () -> task.changeDescription("a".repeat(2001)));
            task.changeDescription(null);
            assertNull(task.description());
        }
    }

    @Test
    void reconstituteRestoresStateWithoutRuleChecks() {
        TaskId id = TaskId.newId();
        ProjectId projectId = ProjectId.newId();
        Instant createdAt = Instant.parse("2024-01-01T10:00:00Z");
        Instant completedAt = Instant.parse("2024-01-02T10:00:00Z");

        Task task = Task.reconstitute(id, projectId, "t", null,
                TaskStatus.DONE, TaskPriority.LOW, createdAt, completedAt);

        assertEquals(id, task.id());
        assertEquals(projectId, task.projectId());
        assertEquals(TaskStatus.DONE, task.status());
        assertEquals(completedAt, task.completedAt());
    }
}
