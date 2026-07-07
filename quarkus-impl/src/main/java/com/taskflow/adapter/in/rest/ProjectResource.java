package com.taskflow.adapter.in.rest;

import com.taskflow.adapter.in.rest.dto.CreateProjectRequest;
import com.taskflow.adapter.in.rest.dto.ProjectResponse;
import com.taskflow.adapter.in.rest.dto.UpdateProjectRequest;
import com.taskflow.adapter.in.rest.validation.BodyValidation;
import com.taskflow.adapter.in.rest.validation.QueryParams;
import com.taskflow.application.usecase.CreateProjectUseCase;
import com.taskflow.application.usecase.GetProjectUseCase;
import com.taskflow.application.usecase.ListProjectsUseCase;
import com.taskflow.application.usecase.UpdateProjectUseCase;
import com.taskflow.domain.model.Project;
import com.taskflow.domain.model.ProjectId;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/projetos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

    private final CreateProjectUseCase createProject;
    private final GetProjectUseCase getProject;
    private final ListProjectsUseCase listProjects;
    private final UpdateProjectUseCase updateProject;

    public ProjectResource(CreateProjectUseCase createProject, GetProjectUseCase getProject,
                           ListProjectsUseCase listProjects, UpdateProjectUseCase updateProject) {
        this.createProject = createProject;
        this.getProject = getProject;
        this.listProjects = listProjects;
        this.updateProject = updateProject;
    }

    @POST
    @Transactional
    public Response create(CreateProjectRequest body) {
        if (body == null) {
            throw BodyValidation.missingBody();
        }
        Project created = createProject.execute(body.toCommand());
        // Location is a relative uri-reference per the spec, so it is set
        // manually — Response.created() would resolve it to an absolute URL.
        return Response.status(Response.Status.CREATED)
                .header("Location", "/projetos/" + created.id().value())
                .entity(ProjectResponse.from(created))
                .build();
    }

    @GET
    public List<ProjectResponse> list(@QueryParam("status") String status) {
        return listProjects.execute(QueryParams.projectStatus(status)).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @GET
    @Path("/{id}")
    public ProjectResponse get(@PathParam("id") String id) {
        // UuidPathParamFilter has already validated the id shape.
        return ProjectResponse.from(getProject.execute(new ProjectId(UUID.fromString(id))));
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    public ProjectResponse update(@PathParam("id") String id, UpdateProjectRequest body) {
        if (body == null) {
            throw BodyValidation.missingBody();
        }
        return ProjectResponse.from(
                updateProject.execute(new ProjectId(UUID.fromString(id)), body.toCommand()));
    }
}
