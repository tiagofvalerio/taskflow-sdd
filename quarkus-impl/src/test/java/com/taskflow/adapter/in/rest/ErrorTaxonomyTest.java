package com.taskflow.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.taskflow.adapter.in.rest.RestTestSupport.PROBLEM_JSON;
import static com.taskflow.adapter.in.rest.RestTestSupport.archiveProject;
import static com.taskflow.adapter.in.rest.RestTestSupport.createProject;
import static com.taskflow.adapter.in.rest.RestTestSupport.createTask;
import static com.taskflow.adapter.in.rest.RestTestSupport.patchTaskStatus;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/**
 * One test per row of the exception-mapping table, plus the spec's precedence rules.
 *
 * <p>Assertion policy (the same policy governs the Rails suite):
 * <ul>
 *   <li><b>Normative — exact equality:</b> {@code type} URI, HTTP status, the
 *       problem's {@code status} member, schema shape, {@code errors[].field},
 *       precedence outcomes.</li>
 *   <li><b>Illustrative — key-token containment only:</b> {@code detail} /
 *       {@code message} prose. OpenAPI {@code examples} are illustrative, not
 *       normative; tokens are chosen to prove the right rule fired.</li>
 *   <li><b>Exception — exact equality:</b> wording the spec's normative
 *       error-reporting policy quotes verbatim (the query-filter details).</li>
 * </ul>
 */
@QuarkusTest
class ErrorTaxonomyTest extends SpecValidatedRestTest {

    private static final String ERR = "https://taskflow.dev/errors/";

    @Nested
    @DisplayName("422 — one type URI per business rule")
    class BusinessRules {

        @Test
        void rule1ArchiveBlockedByInProgressTask() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            patchTaskStatus(taskId, "in_progress");

            given().contentType(ContentType.JSON)
                    .body(Map.of("status", "archived"))
                    .patch("/projetos/{id}", projectId)
                    .then()
                    .statusCode(422)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "project-archive-blocked"))
                    .body("title", equalTo("Projeto não pode ser arquivado"))
                    .body("status", equalTo(422))
                    .body("detail", containsString("in_progress"));
        }

        @Test
        void rule1ArchiveSucceedsOnceNoTaskIsInProgress() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            patchTaskStatus(taskId, "in_progress");
            patchTaskStatus(taskId, "done");

            given().contentType(ContentType.JSON)
                    .body(Map.of("status", "archived"))
                    .patch("/projetos/{id}", projectId)
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("archived"));
        }

        @Test
        void rule2DeleteNonPendingTask() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            patchTaskStatus(taskId, "in_progress");

            given().delete("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(422)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "task-delete-not-pending"))
                    .body("status", equalTo(422))
                    .body("detail", containsString("in_progress"));
        }

        @Test
        void rule2DeleteDoneTask() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            patchTaskStatus(taskId, "in_progress");
            patchTaskStatus(taskId, "done");

            given().delete("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(422)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "task-delete-not-pending"))
                    .body("status", equalTo(422))
                    .body("detail", containsString("done"));
        }

        @Test
        void rule4CreateTaskInArchivedProject() {
            String projectId = createProject("p");
            archiveProject(projectId);

            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "t", "priority", "low"))
                    .post("/projetos/{id}/tarefas", projectId)
                    .then()
                    .statusCode(422)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "task-create-project-archived"))
                    .body("status", equalTo(422))
                    .body("detail", containsString(projectId));
        }

        @Test
        void rule5StatusSkipAhead() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");

            given().contentType(ContentType.JSON)
                    .body(Map.of("status", "done"))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(422)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "task-status-regression"))
                    .body("status", equalTo(422))
                    .body("detail", containsString("avançar um passo"));
        }

        @Test
        void rule5BackwardMovesRejected() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            patchTaskStatus(taskId, "in_progress");
            backwardMoveRejected(taskId, "pending");

            patchTaskStatus(taskId, "done");
            backwardMoveRejected(taskId, "in_progress");
            backwardMoveRejected(taskId, "pending");
        }

        private void backwardMoveRejected(String taskId, String target) {
            given().contentType(ContentType.JSON)
                    .body(Map.of("status", target))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(422)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "task-status-regression"))
                    .body("status", equalTo(422))
                    .body("detail", containsString("retroceder"));
        }

        @Test
        void rule6StatusChangeInArchivedProject() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            archiveProject(projectId);

            given().contentType(ContentType.JSON)
                    .body(Map.of("status", "in_progress"))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(422)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "task-status-change-project-archived"))
                    .body("status", equalTo(422))
                    .body("detail", containsString("estiver arquivado"));
        }

        @Test
        void rule6NonStatusFieldsRemainEditableInArchivedProject() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            archiveProject(projectId);

            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "editável", "priority", "high"))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(200)
                    .body("title", equalTo("editável"))
                    .body("priority", equalTo("high"))
                    .body("status", equalTo("pending"));
        }
    }

    @Nested
    @DisplayName("404 — shared type URI, per-resource detail")
    class NotFound {

        @Test
        void projectNotFound() {
            String unknown = UUID.randomUUID().toString();
            given().get("/projetos/{id}", unknown)
                    .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "resource-not-found"))
                    .body("title", equalTo("Recurso não encontrado"))
                    .body("status", equalTo(404))
                    .body("detail", containsString("projeto"))
                    .body("detail", containsString(unknown));
        }

        @Test
        void taskNotFound() {
            String unknown = UUID.randomUUID().toString();
            given().get("/tarefas/{id}", unknown)
                    .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "resource-not-found"))
                    .body("status", equalTo(404))
                    .body("detail", containsString("tarefa"))
                    .body("detail", containsString(unknown));
        }

        @Test
        void createTaskUnderUnknownProjectReturns404() {
            String unknown = UUID.randomUUID().toString();
            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "t", "priority", "low"))
                    .post("/projetos/{id}/tarefas", unknown)
                    .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "resource-not-found"))
                    .body("detail", containsString(unknown));
        }

        @Test
        void listTasksOfUnknownProjectReturns404() {
            String unknown = UUID.randomUUID().toString();
            given().get("/projetos/{id}/tarefas", unknown)
                    .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "resource-not-found"))
                    .body("detail", containsString(unknown));
        }

        @Test
        void patchUnknownProjectReturns404() {
            String unknown = UUID.randomUUID().toString();
            given().contentType(ContentType.JSON)
                    .body(Map.of("name", "x"))
                    .patch("/projetos/{id}", unknown)
                    .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "resource-not-found"))
                    .body("detail", containsString(unknown));
        }

        @Test
        void deleteUnknownTaskReturns404() {
            String unknown = UUID.randomUUID().toString();
            given().delete("/tarefas/{id}", unknown)
                    .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "resource-not-found"))
                    .body("detail", containsString(unknown));
        }
    }

    @Nested
    @DisplayName("400 — path parameter")
    class PathParam {

        @Test
        void getWithNonUuidIdReturnsPlainProblem() {
            given().get("/projetos/nao-e-um-uuid")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-path-parameter"))
                    .body("detail", containsString("UUID válido"))
                    .body("errors", nullValue());
        }

        @Test
        void getTaskWithNonUuidIdReturnsPlainProblem() {
            given().get("/tarefas/nao-e-um-uuid")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-path-parameter"))
                    .body("detail", containsString("UUID válido"))
                    .body("errors", nullValue());
        }

        @Test
        void deleteWithNonUuidIdReturnsPlainProblem() {
            given().delete("/tarefas/nao-e-um-uuid")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-path-parameter"))
                    .body("detail", containsString("UUID válido"))
                    .body("errors", nullValue());
        }

        @Test
        void patchWithNonUuidIdReportsFieldErrorShape() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "x"))
                    .patch("/tarefas/nao-e-um-uuid")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-path-parameter"))
                    .body("errors[0].field", equalTo("id"))
                    .body("errors[0].message", containsString("UUID válido"));
        }

        @Test
        void patchProjectWithNonUuidIdReportsFieldErrorShape() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("name", "x"))
                    .patch("/projetos/nao-e-um-uuid")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-path-parameter"))
                    .body("errors[0].field", equalTo("id"))
                    .body("errors[0].message", containsString("UUID válido"));
        }

        @Test
        void nonCanonicalUuidAcceptedByUuidFromStringIsStillRejected() {
            given().get("/projetos/1-1-1-1-1")
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-path-parameter"));
        }

        @Test
        void uppercaseHexUuidPassesPathValidation() {
            // Spec: hex digits are case-insensitive; only the 8-4-4-4-12
            // shape is enforced — uppercase must reach the 404 stage, not 400.
            String unknownUpper = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
            given().get("/projetos/{id}", unknownUpper)
                    .then()
                    .statusCode(404)
                    .body("type", equalTo(ERR + "resource-not-found"));
        }
    }

    @Nested
    @DisplayName("400 — query parameter, fail-fast in documented order")
    class QueryParam {

        @Test
        void invalidProjectStatusFilter() {
            given().queryParam("status", "deleted").get("/projetos")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-query-parameter"))
                    .body("detail", equalTo(
                            "O parâmetro status deve ser um dos valores: active, archived."));
        }

        @Test
        void emptyProjectStatusFilterRejected() {
            // "?status=" binds to null via @QueryParam (quarkus-rest quirk,
            // https://github.com/quarkusio/quarkus/issues/44885) — resource
            // reads the raw query map instead so this still fails the enum
            // check rather than being silently treated as "no filter".
            given().queryParam("status", "").get("/projetos")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-query-parameter"))
                    .body("detail", equalTo(
                            "O parâmetro status deve ser um dos valores: active, archived."));
        }

        @Test
        void emptyTaskStatusFilterRejected() {
            String projectId = createProject("p");
            given().queryParam("status", "").get("/projetos/{id}/tarefas", projectId)
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-query-parameter"))
                    .body("detail", equalTo(
                            "O parâmetro status deve ser um dos valores: pending, in_progress, done."));
        }

        @Test
        void bothFiltersInvalidReportsOnlyStatus() {
            String projectId = createProject("p");
            given().queryParam("status", "xxx").queryParam("priority", "yyy")
                    .get("/projetos/{id}/tarefas", projectId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-query-parameter"))
                    .body("detail", containsString("status"))
                    .body("detail", equalTo(
                            "O parâmetro status deve ser um dos valores: pending, in_progress, done."));
        }

        @Test
        void invalidPriorityFilterAloneReportsPriorityDetail() {
            String projectId = createProject("p");
            given().queryParam("priority", "urgent")
                    .get("/projetos/{id}/tarefas", projectId)
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-query-parameter"))
                    .body("detail", equalTo(
                            "O parâmetro priority deve ser um dos valores: low, medium, high."));
        }
    }

    @Nested
    @DisplayName("400 — request body, all violations at once")
    class RequestBody {

        @Test
        void multipleViolationsReportedTogether() {
            String projectId = createProject("p");
            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "", "priority", "urgent"))
                    .post("/projetos/{id}/tarefas", projectId)
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors", hasSize(2))
                    .body("errors.field", hasItems("title", "priority"));
        }

        @Test
        void nullByteInNameRejected() {
            // A JSON \u0000 escape decodes into a real control
            // character that must be rejected here, before it reaches
            // Postgres (which rejects raw NUL bytes with an unhandled 500
            // -- see BodyValidation.hasControlChar).
            given().contentType(ContentType.JSON)
                    .body("{\"name\": \"a\\u0000b\"}")
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("name"));
        }

        @Test
        void unknownFieldRejected() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("name", "x", "foo", "bar"))
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("foo"));
        }

        @Test
        void statusSubmittedOnProjectCreate() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("name", "x", "status", "active"))
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("status"))
                    .body("errors[0].message", containsString("todo projeto novo inicia como active"));
        }

        @Test
        void missingNameOnProjectCreate() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("description", "sem nome"))
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("name"));
        }

        @Test
        void emptyNameOnProjectCreate() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("name", ""))
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("name"));
        }

        @Test
        void oversizedNameOnProjectCreate() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("name", "a".repeat(101)))
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("name"))
                    .body("errors[0].message", containsString("100"));
        }

        @Test
        void oversizedDescriptionOnProjectCreate() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("name", "ok", "description", "d".repeat(2001)))
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("description"))
                    .body("errors[0].message", containsString("2000"));
        }

        @Test
        void missingTitleOnTaskCreate() {
            String projectId = createProject("p");
            given().contentType(ContentType.JSON)
                    .body(Map.of("priority", "low"))
                    .post("/projetos/{id}/tarefas", projectId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("title"));
        }

        @Test
        void missingPriorityOnTaskCreate() {
            String projectId = createProject("p");
            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "t"))
                    .post("/projetos/{id}/tarefas", projectId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("priority"))
                    .body("errors[0].message", containsString("obrigatório"));
        }

        @Test
        void oversizedTitleAndDescriptionOnTaskCreate() {
            String projectId = createProject("p");
            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "t".repeat(201),
                            "description", "d".repeat(2001),
                            "priority", "low"))
                    .post("/projetos/{id}/tarefas", projectId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors", hasSize(2))
                    .body("errors.field", hasItems("title", "description"));
        }

        @Test
        void readOnlyFieldsOnTaskCreateRejected() {
            String projectId = createProject("p");
            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "t", "priority", "low",
                            "status", "done",
                            "completedAt", "2024-01-01T10:00:00Z",
                            "projectId", projectId))
                    .post("/projetos/{id}/tarefas", projectId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors.field", hasItems("status", "completedAt", "projectId"));
        }

        @Test
        void invalidNameOnProjectPatch() {
            String projectId = createProject("p");
            given().contentType(ContentType.JSON)
                    .body(Map.of("name", ""))
                    .patch("/projetos/{id}", projectId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("name"));
        }

        @Test
        void readOnlyFieldsOnProjectPatchRejected() {
            String projectId = createProject("p");
            given().contentType(ContentType.JSON)
                    .body(Map.of("id", UUID.randomUUID().toString(),
                            "createdAt", "2024-01-01T10:00:00Z"))
                    .patch("/projetos/{id}", projectId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors.field", hasItems("id", "createdAt"));
        }

        @Test
        void outOfEnumStatusOnProjectPatch() {
            String projectId = createProject("p");
            given().contentType(ContentType.JSON)
                    .body(Map.of("status", "deleted"))
                    .patch("/projetos/{id}", projectId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("status"))
                    .body("errors[0].message", containsString("active, archived"));
        }

        @Test
        void emptyTitleOnTaskPatch() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            given().contentType(ContentType.JSON)
                    .body(Map.of("title", ""))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("title"));
        }

        @Test
        void outOfEnumStatusStringOnTaskPatch() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            given().contentType(ContentType.JSON)
                    .body(Map.of("status", "cancelled"))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("status"))
                    .body("errors[0].message", containsString("pending, in_progress, done"));
        }

        @Test
        void outOfEnumPriorityStringOnTaskPatch() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            given().contentType(ContentType.JSON)
                    .body(Map.of("priority", "urgent"))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("priority"))
                    .body("errors[0].message", containsString("low, medium, high"));
        }

        @Test
        void completedAtSubmittedOnTaskPatchIsReadOnly() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");

            given().contentType(ContentType.JSON)
                    .body(Map.of("completedAt", "2024-01-01T10:00:00Z"))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("completedAt"))
                    .body("errors[0].message", containsString("somente leitura"));
        }

        @Test
        void numberWhereStringExpectedIsRejectedNamingTheField() {
            given().contentType(ContentType.JSON)
                    .body("{\"name\": 123}")
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("name"));
        }

        @Test
        void numberWhereEnumStringExpectedIsRejectedNamingTheField() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");

            given().contentType(ContentType.JSON)
                    .body("{\"priority\": 1}")
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("priority"));
        }

        @Test
        void malformedJsonIsStillProblemJson() {
            // Spec: syntactically unparseable body always reports field=body.
            given().contentType(ContentType.JSON)
                    .body("{invalid")
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors", hasSize(1))
                    .body("errors[0].field", equalTo("body"));
        }

        @Test
        void multipleUnbindableFieldsAreFailFastInDocumentOrder() {
            // Spec: unbindable values are an exception to the
            // every-violating-field policy — only the first, in document order.
            given().contentType(ContentType.JSON)
                    .body("{\"name\": 5, \"description\": 7}")
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors", hasSize(1))
                    .body("errors[0].field", equalTo("name"));
        }

        @Test
        void wrongShapedFieldValueIsStillProblemJson() {
            given().contentType(ContentType.JSON)
                    .body("{\"name\": {\"nested\": true}}")
                    .post("/projetos")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"));
        }

        @Test
        void emptyPatchBodyRejected() {
            String projectId = createProject("p");
            given().contentType(ContentType.JSON)
                    .body("{}")
                    .patch("/projetos/{id}", projectId)
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("body"));
        }

        @Test
        void emptyPatchBodyOnTaskRejected() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            given().contentType(ContentType.JSON)
                    .body("{}")
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("body"));
        }
    }

    @Nested
    @DisplayName("PATCH atomicity — a 422 persists nothing")
    class Atomicity {

        @Test
        void projectPatchViolatingRule1PersistsNothing() {
            String projectId = createProject("original");
            String taskId = createTask(projectId, "t", "low");
            patchTaskStatus(taskId, "in_progress");

            given().contentType(ContentType.JSON)
                    .body(Map.of("name", "novo nome", "status", "archived"))
                    .patch("/projetos/{id}", projectId)
                    .then()
                    .statusCode(422)
                    .body("type", equalTo(ERR + "project-archive-blocked"));

            given().get("/projetos/{id}", projectId)
                    .then()
                    .statusCode(200)
                    .body("name", equalTo("original"))
                    .body("status", equalTo("active"));
        }

        @Test
        void taskPatchViolatingRule5PersistsNothing() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "original", "low");

            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "novo título", "status", "done"))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(422)
                    .body("type", equalTo(ERR + "task-status-regression"));

            given().get("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(200)
                    .body("title", equalTo("original"))
                    .body("status", equalTo("pending"));
        }
    }

    @Nested
    @DisplayName("Precedence — 400 -> 404 -> 422, rule 6 before rule 5, no-op wins")
    class Precedence {

        @Test
        void pathParamBeatsInvalidBodyOnPatch() {
            // Spec's own example: PATCH /tarefas/nao-e-um-uuid with an invalid
            // body reports ONLY the path problem.
            given().contentType(ContentType.JSON)
                    .body(Map.of("completedAt", "2024-01-01T10:00:00Z"))
                    .patch("/tarefas/nao-e-um-uuid")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-path-parameter"));
        }

        @Test
        void pathParamBeatsInvalidQuery() {
            given().queryParam("status", "xxx")
                    .get("/projetos/nao-e-um-uuid/tarefas")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-path-parameter"));
        }

        @Test
        void badRequestBeats404EvenWhenResourceCouldNeverExist() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "t", "priority", "low"))
                    .post("/projetos/nao-e-um-uuid/tarefas")
                    .then()
                    .statusCode(400)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "invalid-path-parameter"));
        }

        @Test
        void notFoundBeats422() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("status", "done"))
                    .patch("/tarefas/{id}", UUID.randomUUID().toString())
                    .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "resource-not-found"));
        }

        @Test
        void rule6WinsWhenRule5IsAlsoViolated() {
            // pending -> done in an archived project violates 5 AND 6;
            // rule 6's type URI must win, per the spec's 422-stage precedence.
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            archiveProject(projectId);

            given().contentType(ContentType.JSON)
                    .body(Map.of("status", "done"))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(422)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "task-status-change-project-archived"));
        }

        @Test
        void sameStateStatusNoOpBeatsRule6() {
            String projectId = createProject("p");
            String taskId = createTask(projectId, "t", "low");
            archiveProject(projectId);

            given().contentType(ContentType.JSON)
                    .body(Map.of("status", "pending", "title", "renomeada"))
                    .patch("/tarefas/{id}", taskId)
                    .then()
                    .statusCode(200)
                    .body("title", equalTo("renomeada"))
                    .body("status", equalTo("pending"));
        }
    }
}
