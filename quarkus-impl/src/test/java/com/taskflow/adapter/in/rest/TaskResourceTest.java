package com.taskflow.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.taskflow.adapter.in.rest.RestTestSupport.createProject;
import static com.taskflow.adapter.in.rest.RestTestSupport.createTask;
import static com.taskflow.adapter.in.rest.RestTestSupport.patchTaskStatus;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class TaskResourceTest extends SpecValidatedRestTest {

    @Test
    void getReturnsTask() {
        String projectId = createProject("p");
        String taskId = createTask(projectId, "tarefa", "medium");

        given().get("/tarefas/{id}", taskId)
                .then()
                .statusCode(200)
                .body("id", equalTo(taskId))
                .body("projectId", equalTo(projectId));
    }

    @Test
    void fullStatusLifecycleSetsCompletedAtOnDone() {
        String projectId = createProject("p");
        String taskId = createTask(projectId, "tarefa", "medium");

        given().contentType(ContentType.JSON)
                .body(Map.of("status", "in_progress"))
                .patch("/tarefas/{id}", taskId)
                .then().statusCode(200)
                .body("status", equalTo("in_progress"))
                .body("completedAt", nullValue());

        given().contentType(ContentType.JSON)
                .body(Map.of("status", "done"))
                .patch("/tarefas/{id}", taskId)
                .then().statusCode(200)
                .body("status", equalTo("done"))
                .body("completedAt", endsWith("Z"));
    }

    @Test
    void patchEditsNonStatusFields() {
        String projectId = createProject("p");
        String taskId = createTask(projectId, "antes", "low");

        given().contentType(ContentType.JSON)
                .body(Map.of("title", "depois", "priority", "high"))
                .patch("/tarefas/{id}", taskId)
                .then().statusCode(200)
                .body("title", equalTo("depois"))
                .body("priority", equalTo("high"))
                .body("status", equalTo("pending"));
    }

    @Test
    void deletePendingTaskReturns204AndTaskIsGone() {
        String projectId = createProject("p");
        String taskId = createTask(projectId, "descartável", "low");

        given().delete("/tarefas/{id}", taskId).then().statusCode(204);
        given().get("/tarefas/{id}", taskId).then().statusCode(404);
    }

    @Test
    void sameStateStatusIsANoOpEvenWithOtherFields() {
        String projectId = createProject("p");
        String taskId = createTask(projectId, "tarefa", "low");
        patchTaskStatus(taskId, "in_progress");

        given().contentType(ContentType.JSON)
                .body(Map.of("status", "in_progress", "title", "renomeada"))
                .patch("/tarefas/{id}", taskId)
                .then().statusCode(200)
                .body("status", equalTo("in_progress"))
                .body("title", equalTo("renomeada"));
    }
}
