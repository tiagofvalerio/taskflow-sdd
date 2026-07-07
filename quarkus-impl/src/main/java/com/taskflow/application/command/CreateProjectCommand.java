package com.taskflow.application.command;

/** Input for createProject. Status is never accepted on create — every project starts active. */
public record CreateProjectCommand(String name, String description) {
}
