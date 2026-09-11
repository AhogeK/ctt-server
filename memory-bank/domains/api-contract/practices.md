# api-contract — practices

## The two envelopes

```json
// success
{ "success": true, "message": "Operation successful", "data": { }, "timestamp": "<ISO-8601>" }

// error
{ "code": "AUTH_020", "message": "API key missing required scope", "details": [],
  "traceId": "<id>", "httpStatus": 403, "timestamp": "<ISO-8601>" }
```

The success path always wraps the payload in `data` — never return a bare object or array. The error
path is flat: no `success` field, no `data`.

## Declaring an endpoint's contract

```java
@Operation(summary = "...", description = "...")            // what it does, in the reader's terms
@ApiResponses({ @ApiResponse(responseCode = "200", description = "...",
        content = @Content(schema = @Schema(implementation = XResponse.class))),
    @ApiResponse(responseCode = "404", description = "... - COMMON_002",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class),
            examples = @ExampleObject(name = "device-not-found",
                summary = "Device not found or access denied", value = DEVICE_NOT_FOUND_EXAMPLE))) })
@RequiresApiKeyScope(ApiKeyScope.READ)
@RateLimit(type = RateLimitType.API, limit = 60, windowSeconds = 60)
@GetMapping("/x")
public ResponseEntity<RestApiResponse<XResponse>> x(...) { }
```

Reuse the controller's existing example constants (`UNAUTHORIZED_EXAMPLE`,
`SCOPE_DENIED_EXAMPLE`, `DEVICE_NOT_FOUND_EXAMPLE`, `RATE_LIMITED_EXAMPLE`) instead of restating
JSON — the constants are the single source for the envelope shape.

## Insertion traps

| Trap | Symptom | Avoid |
| --- | --- | --- |
| Anchoring a new method before an existing `@GetMapping` | The new `@Operation`/`@RateLimit` stack onto the wrong method, or duplicate annotations | Insert **after** the preceding method's closing brace, then read the region back |
| Adding a param to a controller but not to its test mocks | `cannot be applied to given types` in tests | Grep every caller of the service method after changing its signature |
| Adding a DTO import in a block whose neighbouring line already existed | Duplicate import; a mangled file | Diff the import block after editing |

## Validation patterns

- Path/query binding errors and bean-validation failures both surface as `400 COMMON_003` with a
  descriptive message; throw `ValidationException(COMMON_003, "...")` for cross-field rules
  (e.g. mutually exclusive filters, `end < start`).
- Ownership checks throw `NotFoundException(COMMON_002, "...")` — see principle 3.
- Never `return null` for an empty collection; return an empty list so clients can iterate blindly.

## Test recipe

1. Happy path asserting the wrapped envelope (`$.success`, `$.data...`).
2. Each declared failure status at least once, asserting both status and `$.code`.
3. For a new endpoint: one test asserting the contract **shape** (envelope nesting), because the
   frontend codegen depends on it.
4. For a family change (new shared param): one test per sibling proving they all behave the same.
