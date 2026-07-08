package com.taskflow.adapter.in.rest;

import com.taskflow.adapter.in.rest.dto.CreateTaskRequest;
import com.taskflow.adapter.in.rest.dto.TaskResponse;
import com.taskflow.adapter.in.rest.validation.BodyValidation;
import com.taskflow.adapter.in.rest.validation.QueryParams;
import com.taskflow.application.usecase.CreateTaskUseCase;
import com.taskflow.application.usecase.ListTasksUseCase;
import com.taskflow.domain.model.ProjectId;
import com.taskflow.domain.model.Task;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;
import java.util.UUID;

@Path("/projetos/{id}/tarefas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectTasksResource {

    private final CreateTaskUseCase createTask;
    private final ListTasksUseCase listTasks;

    public ProjectTasksResource(CreateTaskUseCase createTask, ListTasksUseCase listTasks) {
        this.createTask = createTask;
        this.listTasks = listTasks;
    }

    @POST
    @Transactional
    public Response create(@PathParam("id") String projectId, CreateTaskRequest body) {
        if (body == null) {
            throw BodyValidation.missingBody();
        }
        Task created = createTask.execute(
                new ProjectId(UUID.fromString(projectId)), body.toCommand());
        return Response.status(Response.Status.CREATED)
                .header("Location", "/tarefas/" + created.id().value())
                .entity(TaskResponse.from(created))
                .build();
    }

    @GET
    public List<TaskResponse> list(@PathParam("id") String projectId, @Context UriInfo uriInfo) {
        // Spec fail-fast order within the 400 stage: path (filter, already
        // done) -> query, and within query: status before priority.
        //
        // @QueryParam binds a present-but-empty value ("?status=") to null,
        // indistinguishable from an absent parameter (quarkus-rest quirk —
        // https://github.com/quarkusio/quarkus/issues/44885). Reading the raw
        // query map instead preserves the distinction the enum switches need
        // to fail-fast on "?status=" instead of silently ignoring the filter.
        TaskStatus statusFilter = QueryParams.taskStatus(uriInfo.getQueryParameters().getFirst("status"));
        TaskPriority priorityFilter = QueryParams.taskPriority(uriInfo.getQueryParameters().getFirst("priority"));
        return listTasks.execute(new ProjectId(UUID.fromString(projectId)),
                        statusFilter, priorityFilter).stream()
                .map(TaskResponse::from)
                .toList();
    }
}
