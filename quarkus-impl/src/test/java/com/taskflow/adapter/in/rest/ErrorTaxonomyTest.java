package com.taskflow.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

/** One test per row of the exception-mapping table, plus the spec's precedence rules. */
@QuarkusTest
class ErrorTaxonomyTest {

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
                    .body("status", equalTo(422));
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
                    .body("detail", containsString("in_progress"));
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
                    .body("type", equalTo(ERR + "task-status-regression"));
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
                    .body("type", equalTo(ERR + "task-status-change-project-archived"));
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
                    .body("detail", equalTo("Nenhum projeto encontrado com id " + unknown));
        }

        @Test
        void taskNotFound() {
            String unknown = UUID.randomUUID().toString();
            given().get("/tarefas/{id}", unknown)
                    .then()
                    .statusCode(404)
                    .contentType(PROBLEM_JSON)
                    .body("type", equalTo(ERR + "resource-not-found"))
                    .body("detail", equalTo("Nenhuma tarefa encontrada com id " + unknown));
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
                    .body("detail", equalTo("O identificador informado não é um UUID válido."))
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
                    .body("errors[0].message", equalTo("deve ser um UUID válido"));
        }

        @Test
        void nonCanonicalUuidAcceptedByUuidFromStringIsStillRejected() {
            given().get("/projetos/1-1-1-1-1")
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-path-parameter"));
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
                    .body("errors[0].field", equalTo("status"))
                    .body("errors[0].message", containsString("todo projeto novo inicia como active"));
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
        void emptyPatchBodyRejected() {
            String projectId = createProject("p");
            given().contentType(ContentType.JSON)
                    .body("{}")
                    .patch("/projetos/{id}", projectId)
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-request-body"))
                    .body("errors[0].field", equalTo("body"));
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
                    .body("type", equalTo(ERR + "invalid-path-parameter"));
        }

        @Test
        void pathParamBeatsInvalidQuery() {
            given().queryParam("status", "xxx")
                    .get("/projetos/nao-e-um-uuid/tarefas")
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-path-parameter"));
        }

        @Test
        void badRequestBeats404EvenWhenResourceCouldNeverExist() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("title", "t", "priority", "low"))
                    .post("/projetos/nao-e-um-uuid/tarefas")
                    .then()
                    .statusCode(400)
                    .body("type", equalTo(ERR + "invalid-path-parameter"));
        }

        @Test
        void notFoundBeats422() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("status", "done"))
                    .patch("/tarefas/{id}", UUID.randomUUID().toString())
                    .then()
                    .statusCode(404)
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
