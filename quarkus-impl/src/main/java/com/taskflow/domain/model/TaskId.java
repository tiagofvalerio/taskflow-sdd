package com.taskflow.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TaskId(UUID value) {

    public TaskId {
        Objects.requireNonNull(value, "TaskId value must not be null");
    }

    public static TaskId newId() {
        return new TaskId(UUID.randomUUID());
    }
}
