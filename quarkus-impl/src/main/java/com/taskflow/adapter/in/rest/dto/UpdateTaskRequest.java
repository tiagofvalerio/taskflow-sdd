package com.taskflow.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.taskflow.adapter.in.rest.error.FieldError;
import com.taskflow.adapter.in.rest.validation.BodyValidation;
import com.taskflow.adapter.in.rest.validation.InvalidRequestBodyException;
import com.taskflow.application.command.PatchField;
import com.taskflow.application.command.UpdateTaskCommand;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the spec's UpdateTaskRequest. `completedAt` is deliberately NOT a
 * property here — it lands in the unknown-field collector and is reported as
 * a readOnly violation with 400, per business rule 3 and the spec's
 * `additionalProperties: false` decision.
 */
public class UpdateTaskRequest {

    private PatchField<String> title = PatchField.absent();
    private PatchField<String> description = PatchField.absent();
    private PatchField<String> status = PatchField.absent();
    private PatchField<String> priority = PatchField.absent();

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public void setTitle(String value) {
        title = PatchField.ofNullable(value);
    }

    public void setDescription(String value) {
        description = PatchField.ofNullable(value);
    }

    public void setStatus(String value) {
        status = PatchField.ofNullable(value);
    }

    public void setPriority(String value) {
        priority = PatchField.ofNullable(value);
    }

    @JsonAnySetter
    void collectUnknown(String key, Object value) {
        unknownFields.put(key, value);
    }

    public UpdateTaskCommand toCommand() {
        if (!title.isPresent() && !description.isPresent() && !status.isPresent()
                && !priority.isPresent() && unknownFields.isEmpty()) {
            throw new InvalidRequestBodyException(BodyValidation.DETAIL_EMPTY_PATCH,
                    List.of(new FieldError("body", BodyValidation.EMPTY_PATCH_BODY)));
        }

        List<FieldError> errors = new ArrayList<>();
        PatchField<TaskStatus> parsedStatus = PatchField.absent();
        PatchField<TaskPriority> parsedPriority = PatchField.absent();
        if (title.isPresent() && BodyValidation.invalidText(title.value(), 200)) {
            errors.add(new FieldError("title", BodyValidation.TITLE_INVALID));
        }
        if (description.isPresent() && BodyValidation.invalidDescription(description.value(), 2000)) {
            errors.add(new FieldError("description", BodyValidation.DESCRIPTION_TOO_LONG));
        }
        if (status.isPresent()) {
            switch (status.value() == null ? "" : status.value()) {
                case "pending" -> parsedStatus = PatchField.ofNullable(TaskStatus.PENDING);
                case "in_progress" -> parsedStatus = PatchField.ofNullable(TaskStatus.IN_PROGRESS);
                case "done" -> parsedStatus = PatchField.ofNullable(TaskStatus.DONE);
                default -> errors.add(new FieldError("status", BodyValidation.TASK_STATUS_INVALID));
            }
        }
        if (priority.isPresent()) {
            switch (priority.value() == null ? "" : priority.value()) {
                case "low" -> parsedPriority = PatchField.ofNullable(TaskPriority.LOW);
                case "medium" -> parsedPriority = PatchField.ofNullable(TaskPriority.MEDIUM);
                case "high" -> parsedPriority = PatchField.ofNullable(TaskPriority.HIGH);
                default -> errors.add(new FieldError("priority", BodyValidation.PRIORITY_INVALID));
            }
        }
        for (String field : unknownFields.keySet()) {
            errors.add(new FieldError(field, BodyValidation.READ_ONLY_FIELDS.contains(field)
                    ? BodyValidation.READ_ONLY : BodyValidation.UNKNOWN_FIELD));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestBodyException(unknownFields.isEmpty()
                    ? BodyValidation.DETAIL_TASK_FIELDS
                    : BodyValidation.DETAIL_UNKNOWN_ON_PATCH, errors);
        }
        return new UpdateTaskCommand(title, description, parsedStatus, parsedPriority);
    }
}
