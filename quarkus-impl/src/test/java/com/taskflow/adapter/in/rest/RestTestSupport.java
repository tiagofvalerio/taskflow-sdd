package com.taskflow.adapter.in.rest;

import io.restassured.http.ContentType;

import java.util.Map;

import static io.restassured.RestAssured.given;

/** Seeds state through the public API itself — tests exercise the full stack. */
final class RestTestSupport {

    static final String PROBLEM_JSON = "application/problem+json";

    private RestTestSupport() {
    }

    static String createProject(String name) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("name", name))
                .post("/projetos")
                .then().statusCode(201)
                .extract().path("id");
    }

    static String createTask(String projectId, String title, String priority) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("title", title, "priority", priority))
                .post("/projetos/{id}/tarefas", projectId)
                .then().statusCode(201)
                .extract().path("id");
    }

    static void patchTaskStatus(String taskId, String status) {
        given().contentType(ContentType.JSON)
                .body(Map.of("status", status))
                .patch("/tarefas/{id}", taskId)
                .then().statusCode(200);
    }

    static void archiveProject(String projectId) {
        given().contentType(ContentType.JSON)
                .body(Map.of("status", "archived"))
                .patch("/projetos/{id}", projectId)
                .then().statusCode(200);
    }
}
