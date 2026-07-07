package com.taskflow.adapter.in.rest;

import com.taskflow.adapter.in.rest.dto.TaskResponse;
import com.taskflow.adapter.in.rest.dto.UpdateTaskRequest;
import com.taskflow.adapter.in.rest.validation.BodyValidation;
import com.taskflow.application.usecase.DeleteTaskUseCase;
import com.taskflow.application.usecase.GetTaskUseCase;
import com.taskflow.application.usecase.UpdateTaskUseCase;
import com.taskflow.domain.model.TaskId;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/tarefas/{id}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskResource {

    private final GetTaskUseCase getTask;
    private final UpdateTaskUseCase updateTask;
    private final DeleteTaskUseCase deleteTask;

    public TaskResource(GetTaskUseCase getTask, UpdateTaskUseCase updateTask,
                        DeleteTaskUseCase deleteTask) {
        this.getTask = getTask;
        this.updateTask = updateTask;
        this.deleteTask = deleteTask;
    }

    @GET
    public TaskResponse get(@PathParam("id") String id) {
        // UuidPathParamFilter has already validated the id shape.
        return TaskResponse.from(getTask.execute(new TaskId(UUID.fromString(id))));
    }

    @PATCH
    @Transactional
    public TaskResponse update(@PathParam("id") String id, UpdateTaskRequest body) {
        if (body == null) {
            throw BodyValidation.missingBody();
        }
        return TaskResponse.from(
                updateTask.execute(new TaskId(UUID.fromString(id)), body.toCommand()));
    }

    @DELETE
    @Transactional
    public Response delete(@PathParam("id") String id) {
        deleteTask.execute(new TaskId(UUID.fromString(id)));
        return Response.noContent().build();
    }
}
