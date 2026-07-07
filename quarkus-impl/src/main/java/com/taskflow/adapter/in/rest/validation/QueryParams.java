package com.taskflow.adapter.in.rest.validation;

import com.taskflow.domain.model.ProjectStatus;
import com.taskflow.domain.model.TaskPriority;
import com.taskflow.domain.model.TaskStatus;

/**
 * Query filter parsing. Callers must invoke these in the operation's
 * documented parameter order (status before priority) — the first failure
 * throws, and only that one is ever reported (spec fail-fast policy).
 */
public final class QueryParams {

    private QueryParams() {
    }

    public static ProjectStatus projectStatus(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "active" -> ProjectStatus.ACTIVE;
            case "archived" -> ProjectStatus.ARCHIVED;
            default -> throw new InvalidQueryParamException(
                    "O parâmetro status deve ser um dos valores: active, archived.");
        };
    }

    public static TaskStatus taskStatus(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "pending" -> TaskStatus.PENDING;
            case "in_progress" -> TaskStatus.IN_PROGRESS;
            case "done" -> TaskStatus.DONE;
            default -> throw new InvalidQueryParamException(
                    "O parâmetro status deve ser um dos valores: pending, in_progress, done.");
        };
    }

    public static TaskPriority taskPriority(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "low" -> TaskPriority.LOW;
            case "medium" -> TaskPriority.MEDIUM;
            case "high" -> TaskPriority.HIGH;
            default -> throw new InvalidQueryParamException(
                    "O parâmetro priority deve ser um dos valores: low, medium, high.");
        };
    }
}
