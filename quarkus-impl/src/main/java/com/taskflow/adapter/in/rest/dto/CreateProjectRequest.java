package com.taskflow.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.taskflow.adapter.in.rest.error.FieldError;
import com.taskflow.adapter.in.rest.validation.BodyValidation;
import com.taskflow.adapter.in.rest.validation.InvalidRequestBodyException;
import com.taskflow.application.command.CreateProjectCommand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the spec's CreateProjectRequest. Unknown keys are collected (not
 * rejected mid-parse by Jackson) so the response can report every violating
 * field at once — the spec's `additionalProperties: false` enforcement lives
 * in {@link #toCommand()}.
 */
public class CreateProjectRequest {

    public String name;
    public String description;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    @JsonAnySetter
    void collectUnknown(String key, Object value) {
        unknownFields.put(key, value);
    }

    public CreateProjectCommand toCommand() {
        List<FieldError> errors = new ArrayList<>();
        if (BodyValidation.invalidText(name, 100)) {
            errors.add(new FieldError("name", BodyValidation.NAME_INVALID));
        }
        if (BodyValidation.tooLong(description, 2000)) {
            errors.add(new FieldError("description", BodyValidation.DESCRIPTION_TOO_LONG));
        }
        for (String field : unknownFields.keySet()) {
            errors.add(new FieldError(field, switch (field) {
                case "status" -> BodyValidation.STATUS_ON_CREATE_PROJECT;
                default -> BodyValidation.READ_ONLY_FIELDS.contains(field)
                        ? BodyValidation.READ_ONLY : BodyValidation.UNKNOWN_FIELD;
            }));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestBodyException(unknownFields.isEmpty()
                    ? BodyValidation.DETAIL_PROJECT_FIELDS
                    : BodyValidation.DETAIL_UNKNOWN_ON_CREATE, errors);
        }
        return new CreateProjectCommand(name, description);
    }
}
