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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ProjectTasksResourceTest {

    @Test
    void createReturns201WithRelativeLocationAndPendingStatus() {
        String projectId = createProject("com tarefas");
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("title", "escrever adapters", "priority", "high"))
                .post("/projetos/{id}/tarefas", projectId)
                .then()
                .statusCode(201)
                .body("title", equalTo("escrever adapters"))
                .body("status", equalTo("pending"))
                .body("priority", equalTo("high"))
                .body("projectId", equalTo(projectId))
                .body("completedAt", nullValue())
                .body("createdAt", endsWith("Z"))
                .extract();

        assertEquals("/tarefas/" + response.path("id"), response.header("Location"));
    }

    @Test
    void listReturnsOnlyThatProjectsTasksAndHonorsFilters() {
        String projectId = createProject("filtros");
        String pendingLow = createTask(projectId, "pendente baixa", "low");
        String doneHigh = createTask(projectId, "concluída alta", "high");
        patchTaskStatus(doneHigh, "in_progress");
        patchTaskStatus(doneHigh, "done");
        String otherProject = createProject("outro");
        String otherTask = createTask(otherProject, "de outro projeto", "low");

        given().get("/projetos/{id}/tarefas", projectId)
                .then().statusCode(200)
                .body("id", hasItems(pendingLow, doneHigh))
                .body("id", not(hasItems(otherTask)));

        given().queryParam("status", "pending")
                .queryParam("priority", "low")
                .get("/projetos/{id}/tarefas", projectId)
                .then().statusCode(200)
                .body("id", hasItems(pendingLow))
                .body("id", not(hasItems(doneHigh)));
    }
}
