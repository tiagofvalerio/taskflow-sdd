package com.taskflow.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.taskflow.adapter.in.rest.error.FieldError;
import com.taskflow.adapter.in.rest.validation.BodyValidation;
import com.taskflow.adapter.in.rest.validation.InvalidRequestBodyException;
import com.taskflow.application.command.CreateTaskCommand;
import com.taskflow.domain.model.TaskPriority;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mirrors the spec's CreateTaskRequest; `priority` is required, no implicit default. */
public class CreateTaskRequest {

    public String title;
    public String description;
    public String priority;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    @JsonAnySetter
    void collectUnknown(String key, Object value) {
        unknownFields.put(key, value);
    }

    public CreateTaskCommand toCommand() {
        List<FieldError> errors = new ArrayList<>();
        TaskPriority parsedPriority = null;
        if (BodyValidation.invalidText(title, 200)) {
            errors.add(new FieldError("title", BodyValidation.TITLE_INVALID));
        }
        if (BodyValidation.invalidDescription(description, 2000)) {
            errors.add(new FieldError("description", BodyValidation.DESCRIPTION_TOO_LONG));
        }
        switch (priority == null ? "" : priority) {
            case "low" -> parsedPriority = TaskPriority.LOW;
            case "medium" -> parsedPriority = TaskPriority.MEDIUM;
            case "high" -> parsedPriority = TaskPriority.HIGH;
            case "" -> errors.add(new FieldError("priority", BodyValidation.PRIORITY_REQUIRED));
            default -> errors.add(new FieldError("priority", BodyValidation.PRIORITY_INVALID));
        }
        for (String field : unknownFields.keySet()) {
            errors.add(new FieldError(field, switch (field) {
                case "status" -> BodyValidation.STATUS_ON_CREATE_TASK;
                default -> BodyValidation.READ_ONLY_FIELDS.contains(field)
                        ? BodyValidation.READ_ONLY : BodyValidation.UNKNOWN_FIELD;
            }));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestBodyException(unknownFields.isEmpty()
                    ? BodyValidation.DETAIL_TASK_FIELDS
                    : BodyValidation.DETAIL_UNKNOWN_ON_CREATE, errors);
        }
        return new CreateTaskCommand(title, description, parsedPriority);
    }
}
