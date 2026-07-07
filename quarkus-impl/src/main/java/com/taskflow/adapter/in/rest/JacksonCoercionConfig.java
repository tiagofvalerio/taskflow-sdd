package com.taskflow.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * The spec's request schemas are typed (`type: string`); Jackson's default
 * scalar coercion would silently accept {"name": 123} as "123", drifting from
 * the contract (found by /sdd-check). Failing the coercion turns it into a
 * MismatchedInputException, which the error mappers already surface as the
 * 400 invalid-request-body problem naming the field. Scoped to textual
 * targets: request DTOs have no numeric fields, so the reverse direction has
 * nothing to configure, and serialization is unaffected.
 */
@Singleton
public class JacksonCoercionConfig implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }
}
