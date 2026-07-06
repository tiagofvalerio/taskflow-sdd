package com.taskflow.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ProjectId(UUID value) {

    public ProjectId {
        Objects.requireNonNull(value, "ProjectId value must not be null");
    }

    public static ProjectId newId() {
        return new ProjectId(UUID.randomUUID());
    }
}
