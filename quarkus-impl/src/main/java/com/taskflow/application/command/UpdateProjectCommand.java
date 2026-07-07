package com.taskflow.application.command;

import com.taskflow.domain.model.ProjectStatus;

import java.util.Objects;

/** Partial-update input for updateProject; each field is tri-state (see {@link PatchField}). */
public record UpdateProjectCommand(PatchField<String> name,
                                   PatchField<String> description,
                                   PatchField<ProjectStatus> status) {

    public UpdateProjectCommand {
        Objects.requireNonNull(name, "name field must not be null (use PatchField.absent())");
        Objects.requireNonNull(description, "description field must not be null (use PatchField.absent())");
        Objects.requireNonNull(status, "status field must not be null (use PatchField.absent())");
    }
}
