---
name: sdd-check
description: Verify current implementation strictly conforms to spec/openapi.yaml; report drift
disable-model-invocation: true
---
Compare the implementation in $ARGUMENTS (quarkus-impl or rails-impl) against
spec/openapi.yaml. Check systematically:

1. Every path+method in the spec exists in the code (and nothing extra exists)
2. Request/response schemas match (field names, types, required, enums)
3. Every documented error response (400, 404, 422) has a code path producing it,
   with the exact ProblemDetails `type` URI from the spec
4. Status codes match exactly (201 vs 200, 204 on delete)
5. Query params (status, priority filters) match spec definitions

Output a conformance table: endpoint | spec ✓/✗ | drift description.
Do NOT fix anything — report only. The spec is the source of truth; if code
drifted, code gets fixed, unless I explicitly decide to amend the spec.
