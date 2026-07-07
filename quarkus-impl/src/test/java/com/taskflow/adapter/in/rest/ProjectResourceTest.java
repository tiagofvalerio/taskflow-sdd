package com.taskflow.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.taskflow.adapter.in.rest.RestTestSupport.archiveProject;
import static com.taskflow.adapter.in.rest.RestTestSupport.createProject;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ProjectResourceTest extends SpecValidatedRestTest {

    @Test
    void createReturns201WithRelativeLocationAndZSuffixedCreatedAt() {
        var response = given().contentType(ContentType.JSON)
                .body(Map.of("name", "TaskFlow", "description", "desafio"))
                .post("/projetos")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .body("name", equalTo("TaskFlow"))
                .body("description", equalTo("desafio"))
                .body("status", equalTo("active"))
                .body("createdAt", endsWith("Z"))
                .body("createdAt", matchesRegex("\\d{4}-\\d{2}-\\d{2}T.*Z"))
                .extract();

        assertEquals("/projetos/" + response.path("id"), response.header("Location"));
    }

    @Test
    void getReturnsProject() {
        String id = createProject("busca");
        given().get("/projetos/{id}", id)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(id))
                .body("name", equalTo("busca"));
    }

    @Test
    void listReturnsProjectsAndHonorsStatusFilter() {
        String activeId = createProject("ativo");
        String archivedId = createProject("arquivado");
        archiveProject(archivedId);

        given().get("/projetos")
                .then().statusCode(200)
                .body("id", hasItems(activeId, archivedId));

        given().queryParam("status", "archived").get("/projetos")
                .then().statusCode(200)
                .body("id", hasItems(archivedId))
                .body("id", not(hasItems(activeId)));
    }

    @Test
    void createWithNullDescriptionReturns201() {
        given().contentType(ContentType.JSON)
                .body("{\"name\": \"sem descrição\", \"description\": null}")
                .post("/projetos")
                .then()
                .statusCode(201)
                .body("name", equalTo("sem descrição"))
                .body("description", nullValue());
    }

    @Test
    void listOrdersByCreatedAtAscending() {
        String first = createProject("ordem 1");
        String second = createProject("ordem 2");
        String third = createProject("ordem 3");

        List<String> ids = given().get("/projetos")
                .then().statusCode(200)
                .extract().jsonPath().getList("id", String.class);

        assertTrue(ids.indexOf(first) < ids.indexOf(second));
        assertTrue(ids.indexOf(second) < ids.indexOf(third));
    }

    @Test
    void unrecognizedQueryParamIsIgnored() {
        String id = createProject("tolerante");
        given().queryParam("foo", "bar").get("/projetos")
                .then().statusCode(200)
                .body("id", hasItems(id));
    }

    @Test
    void sameStateStatusNoOpAppliesSiblingField() {
        // Spec's own worked example: {"status": "active", "name": "New Name"}
        // on an already-active project updates name, leaves status untouched.
        String id = createProject("antes do no-op");
        given().contentType(ContentType.JSON)
                .body(Map.of("status", "active", "name", "New Name"))
                .patch("/projetos/{id}", id)
                .then()
                .statusCode(200)
                .body("name", equalTo("New Name"))
                .body("status", equalTo("active"));
    }

    @Test
    void patchUpdatesFieldsAndClearsDescriptionWithExplicitNull() {
        String id = createProject("antes");
        given().contentType(ContentType.JSON)
                .body("{\"name\": \"depois\", \"description\": null}")
                .patch("/projetos/{id}", id)
                .then()
                .statusCode(200)
                .body("name", equalTo("depois"))
                .body("description", nullValue());
    }

    @Test
    void archiveAndUnarchiveRoundTrip() {
        String id = createProject("arquivável");
        archiveProject(id);
        given().get("/projetos/{id}", id).then().body("status", equalTo("archived"));

        given().contentType(ContentType.JSON)
                .body(Map.of("status", "active"))
                .patch("/projetos/{id}", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("active"));
    }
}
