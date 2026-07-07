package com.taskflow.adapter.in.rest.validation;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates every `id` path parameter as a canonical UUID BEFORE the resource
 * method runs — and therefore before Jackson reads the body. This is what
 * enforces the spec's 400-stage sub-order (path before body); without it,
 * JAX-RS would deserialize the body first and a malformed body would win.
 *
 * UUID.fromString is deliberately not used for validation: it accepts
 * non-canonical forms like "1-1-1-1-1".
 */
@Provider
public class UuidPathParamFilter implements ContainerRequestFilter {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Override
    public void filter(ContainerRequestContext requestContext) {
        List<String> ids = requestContext.getUriInfo().getPathParameters().get("id");
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (!CANONICAL_UUID.matcher(id).matches()) {
                String method = requestContext.getMethod();
                boolean bodyEndpoint = method.equals("POST") || method.equals("PATCH");
                throw new InvalidPathParamException(bodyEndpoint);
            }
        }
    }
}
