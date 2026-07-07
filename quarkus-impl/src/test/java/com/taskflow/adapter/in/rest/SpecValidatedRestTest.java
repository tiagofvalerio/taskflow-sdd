package com.taskflow.adapter.in.rest;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import io.restassured.RestAssured;

import java.nio.file.Path;

/**
 * Base class for REST contract tests: every response is validated against
 * spec/openapi.yaml (the single source of truth) by a global RestAssured
 * filter, in addition to each test's own assertions.
 *
 * <p>Request-side validation is disabled on purpose — the 400-taxonomy tests
 * deliberately send malformed requests, which must reach the server instead
 * of being rejected client-side by the filter.
 *
 * <p>{@code withResolveCombinators(true)} merges {@code allOf} compositions
 * (ValidationProblemDetails) before validating; without it the validator's
 * injected {@code additionalProperties: false} fails each branch separately
 * (see the swagger-request-validator FAQ).
 */
abstract class SpecValidatedRestTest {

    static {
        var validator = OpenApiInteractionValidator
                .createForSpecificationUrl(specUrl())
                .withResolveCombinators(true)
                .withLevelResolver(LevelResolver.create()
                        .withLevel("validation.request", ValidationReport.Level.IGNORE)
                        .build())
                .build();
        RestAssured.filters(new OpenApiValidationFilter(validator));
    }

    private static String specUrl() {
        return Path.of("..", "spec", "openapi.yaml")
                .toAbsolutePath().normalize().toUri().toString();
    }
}
