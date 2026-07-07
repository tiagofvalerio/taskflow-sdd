package com.taskflow.adapter.in.rest.error;

import com.taskflow.adapter.in.rest.validation.InvalidPathParamException;
import com.taskflow.adapter.in.rest.validation.InvalidQueryParamException;
import com.taskflow.adapter.in.rest.validation.InvalidRequestBodyException;
import com.taskflow.application.exception.ProjectNotFoundException;
import com.taskflow.application.exception.TaskNotFoundException;
import com.taskflow.domain.exception.DomainRuleViolationException;
import com.taskflow.domain.exception.ProjectArchiveBlockedException;
import com.taskflow.domain.exception.TaskCreateInArchivedProjectException;
import com.taskflow.domain.exception.TaskDeleteNotPendingException;
import com.taskflow.domain.exception.TaskStatusChangeBlockedException;
import com.taskflow.domain.exception.TaskStatusRegressionException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.Locale;

/**
 * The spec's full error taxonomy: every business rule maps to its own 422
 * type URI; 404s share the resource-not-found type with per-resource detail;
 * the three 400 categories each have their own type. All bodies are RFC 7807
 * with application/problem+json and Portuguese title/detail.
 */
public final class ErrorMappers {

    private static final String ERRORS_BASE = "https://taskflow.dev/errors/";

    private ErrorMappers() {
    }

    private static String instanceOf(UriInfo uriInfo) {
        return "/" + uriInfo.getPath();
    }

    private static Response problem(int status, String type, String title, String detail,
                                    UriInfo uriInfo) {
        return Response.status(status)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(new ProblemDetails(ERRORS_BASE + type, title, status, detail,
                        instanceOf(uriInfo)))
                .build();
    }

    private static Response validationProblem(String type, String title, String detail,
                                              List<FieldError> errors, UriInfo uriInfo) {
        return Response.status(400)
                .type(ProblemDetails.MEDIA_TYPE)
                .entity(new ValidationProblemDetails(ERRORS_BASE + type, title, 400, detail,
                        instanceOf(uriInfo), errors))
                .build();
    }

    @Provider
    public static class DomainRuleMapper implements ExceptionMapper<DomainRuleViolationException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(DomainRuleViolationException exception) {
            return switch (exception) {
                case ProjectArchiveBlockedException e -> problem(422,
                        "project-archive-blocked",
                        "Projeto não pode ser arquivado",
                        "O projeto possui ao menos uma tarefa com status in_progress.",
                        uriInfo);
                case TaskDeleteNotPendingException e -> problem(422,
                        "task-delete-not-pending",
                        "Tarefa não pode ser excluída",
                        "Apenas tarefas com status pending podem ser excluídas; esta tarefa está %s."
                                .formatted(e.currentStatus().name().toLowerCase(Locale.ROOT)),
                        uriInfo);
                case TaskCreateInArchivedProjectException e -> problem(422,
                        "task-create-project-archived",
                        "Não é possível criar tarefa em projeto arquivado",
                        "O projeto %s está arquivado e não aceita novas tarefas."
                                .formatted(e.projectId().value()),
                        uriInfo);
                case TaskStatusRegressionException e -> problem(422,
                        "task-status-regression",
                        "Transição de status inválida",
                        "A transição de status só pode avançar um passo por vez"
                                + " (pending -> in_progress -> done); não é permitido"
                                + " retroceder nem pular etapas.",
                        uriInfo);
                case TaskStatusChangeBlockedException e -> problem(422,
                        "task-status-change-project-archived",
                        "Não é possível alterar o status de tarefa em projeto arquivado",
                        "O status desta tarefa não pode ser alterado enquanto seu projeto"
                                + " estiver arquivado; title, description e priority"
                                + " continuam editáveis.",
                        uriInfo);
            };
        }
    }

    @Provider
    public static class ProjectNotFoundMapper implements ExceptionMapper<ProjectNotFoundException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(ProjectNotFoundException exception) {
            return problem(404, "resource-not-found", "Recurso não encontrado",
                    "Nenhum projeto encontrado com id %s".formatted(exception.projectId().value()),
                    uriInfo);
        }
    }

    @Provider
    public static class TaskNotFoundMapper implements ExceptionMapper<TaskNotFoundException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(TaskNotFoundException exception) {
            return problem(404, "resource-not-found", "Recurso não encontrado",
                    "Nenhuma tarefa encontrada com id %s".formatted(exception.taskId().value()),
                    uriInfo);
        }
    }

    @Provider
    public static class InvalidPathParamMapper implements ExceptionMapper<InvalidPathParamException> {

        private static final String TITLE = "Parâmetro de path inválido";
        private static final String DETAIL = "O identificador informado não é um UUID válido.";

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(InvalidPathParamException exception) {
            if (exception.reportAsFieldError()) {
                return validationProblem("invalid-path-parameter", TITLE, DETAIL,
                        List.of(new FieldError("id", "deve ser um UUID válido")), uriInfo);
            }
            return problem(400, "invalid-path-parameter", TITLE, DETAIL, uriInfo);
        }
    }

    @Provider
    public static class InvalidQueryParamMapper implements ExceptionMapper<InvalidQueryParamException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(InvalidQueryParamException exception) {
            return problem(400, "invalid-query-parameter", "Parâmetro de query inválido",
                    exception.detail(), uriInfo);
        }
    }

    @Provider
    public static class InvalidRequestBodyMapper implements ExceptionMapper<InvalidRequestBodyException> {

        @Context
        UriInfo uriInfo;

        @Override
        public Response toResponse(InvalidRequestBodyException exception) {
            return validationProblem("invalid-request-body", "Corpo da requisição inválido",
                    exception.detail(), exception.errors(), uriInfo);
        }
    }
}
