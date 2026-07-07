package com.taskflow.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.taskflow.adapter.in.rest.error.FieldError;
import com.taskflow.adapter.in.rest.validation.BodyValidation;
import com.taskflow.adapter.in.rest.validation.InvalidRequestBodyException;
import com.taskflow.application.command.PatchField;
import com.taskflow.application.command.UpdateProjectCommand;
import com.taskflow.domain.model.ProjectStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the spec's UpdateProjectRequest. Jackson only calls a setter when
 * the key is present in the JSON, which is exactly the tri-state PatchField
 * needs: absent / explicit null / value.
 */
public class UpdateProjectRequest {

    private PatchField<String> name = PatchField.absent();
    private PatchField<String> description = PatchField.absent();
    private PatchField<String> status = PatchField.absent();

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public void setName(String value) {
        name = PatchField.ofNullable(value);
    }

    public void setDescription(String value) {
        description = PatchField.ofNullable(value);
    }

    public void setStatus(String value) {
        status = PatchField.ofNullable(value);
    }

    @JsonAnySetter
    void collectUnknown(String key, Object value) {
        unknownFields.put(key, value);
    }

    public UpdateProjectCommand toCommand() {
        if (!name.isPresent() && !description.isPresent() && !status.isPresent()
                && unknownFields.isEmpty()) {
            throw new InvalidRequestBodyException(BodyValidation.DETAIL_EMPTY_PATCH,
                    List.of(new FieldError("body", BodyValidation.EMPTY_PATCH_BODY)));
        }

        List<FieldError> errors = new ArrayList<>();
        PatchField<ProjectStatus> parsedStatus = PatchField.absent();
        if (name.isPresent() && BodyValidation.invalidText(name.value(), 100)) {
            errors.add(new FieldError("name", BodyValidation.NAME_INVALID));
        }
        if (description.isPresent() && BodyValidation.tooLong(description.value(), 2000)) {
            errors.add(new FieldError("description", BodyValidation.DESCRIPTION_TOO_LONG));
        }
        if (status.isPresent()) {
            switch (status.value() == null ? "" : status.value()) {
                case "active" -> parsedStatus = PatchField.ofNullable(ProjectStatus.ACTIVE);
                case "archived" -> parsedStatus = PatchField.ofNullable(ProjectStatus.ARCHIVED);
                default -> errors.add(new FieldError("status", BodyValidation.PROJECT_STATUS_INVALID));
            }
        }
        for (String field : unknownFields.keySet()) {
            errors.add(new FieldError(field, BodyValidation.READ_ONLY_FIELDS.contains(field)
                    ? BodyValidation.READ_ONLY : BodyValidation.UNKNOWN_FIELD));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestBodyException(unknownFields.isEmpty()
                    ? BodyValidation.DETAIL_PROJECT_FIELDS
                    : BodyValidation.DETAIL_UNKNOWN_ON_PATCH, errors);
        }
        return new UpdateProjectCommand(name, description, parsedStatus);
    }
}
