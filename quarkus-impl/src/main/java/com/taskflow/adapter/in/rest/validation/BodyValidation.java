package com.taskflow.adapter.in.rest.validation;

import com.taskflow.adapter.in.rest.error.FieldError;

import java.util.List;
import java.util.Set;

/** Portuguese messages and shared checks — texts mirror the spec's examples. */
public final class BodyValidation {

    public static final String NAME_INVALID =
            "não pode estar em branco e deve ter no máximo 100 caracteres";
    public static final String TITLE_INVALID =
            "não pode estar em branco e deve ter no máximo 200 caracteres";
    public static final String DESCRIPTION_TOO_LONG =
            "deve ter no máximo 2000 caracteres";
    public static final String PRIORITY_REQUIRED =
            "é obrigatório e deve ser um dos valores: low, medium, high";
    public static final String PRIORITY_INVALID =
            "deve ser um dos valores: low, medium, high";
    public static final String TASK_STATUS_INVALID =
            "deve ser um dos valores: pending, in_progress, done";
    public static final String PROJECT_STATUS_INVALID =
            "deve ser um dos valores: active, archived";
    public static final String READ_ONLY =
            "campo somente leitura; não pode ser definido pelo client";
    public static final String UNKNOWN_FIELD =
            "campo desconhecido";
    public static final String STATUS_ON_CREATE_PROJECT =
            "campo não aceito na criação; todo projeto novo inicia como active";
    public static final String STATUS_ON_CREATE_TASK =
            "campo não aceito na criação; toda tarefa nova inicia como pending";
    public static final String EMPTY_PATCH_BODY =
            "deve conter ao menos uma propriedade patchable";

    public static final String DETAIL_PROJECT_FIELDS =
            "Um ou mais campos do projeto são inválidos.";
    public static final String DETAIL_TASK_FIELDS =
            "Um ou mais campos da tarefa são inválidos.";
    public static final String DETAIL_UNKNOWN_ON_CREATE =
            "O corpo da requisição contém um campo desconhecido ou não aceito na criação.";
    public static final String DETAIL_UNKNOWN_ON_PATCH =
            "O corpo da requisição contém um campo desconhecido ou somente leitura.";
    public static final String DETAIL_EMPTY_PATCH =
            "O corpo do PATCH não pode ser vazio; inclua ao menos um campo patchable.";
    public static final String DETAIL_MISSING_BODY =
            "O corpo da requisição é obrigatório.";

    /** Server-assigned fields — submitting any of them is a readOnly violation. */
    public static final Set<String> READ_ONLY_FIELDS =
            Set.of("id", "createdAt", "completedAt", "projectId");

    private BodyValidation() {
    }

    public static boolean invalidText(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    public static boolean tooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    public static InvalidRequestBodyException missingBody() {
        return new InvalidRequestBodyException(DETAIL_MISSING_BODY,
                List.of(new FieldError("body", "não pode ser vazio")));
    }
}
