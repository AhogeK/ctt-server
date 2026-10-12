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

## A null field is absent by default — opt out when the key is part of the contract

`spring.jackson.default-property-inclusion: non_null` (`application.yaml`) drops null fields from
every response. Right default for optional extras, wrong one for a field whose *presence* the client
reads:

- **Absent means "your client is older than this field"; null means "the field exists and has no
  value".** Collapsing the two makes "no baseline" indistinguishable from "unknown field", which is
  exactly what a client needs to tell apart to decide whether to render or hide an element.
- Primitives survive the policy by accident (`long 0` is not null) — that is why aggregate counters
  are primitives, and why a test asserting the key exists **at zero** is worth keeping.
- A legitimately nullable field opts out on the record component:
  `@JsonInclude(JsonInclude.Include.ALWAYS)` (with `@Schema(nullable = true)` so the docs agree).
  Annotating the component is enough — Jackson reads record-component annotations.

**Assert it as a key, not as a value.** `extractingPath("$.x.y").isNull()` passes for a *missing*
key too, so it cannot detect the policy silently dropping the field. Assert on the containing map:
`extractingPath("$.x").asMap().containsEntry("y", null)` — and for a family of always-present keys,
`asMap().containsOnlyKeys(...)`, which also catches renames.

**An `example` must be a state the code can actually emit.** Generated docs are what a client
integrator reads first, so a sample that pairs a non-zero `previousSeconds` with a `null` percent —
a combination the DTO never produces, because null requires a zero baseline — teaches them to handle
a case that cannot happen while hiding the one that does. When a field's value is conditional, put
the sample on the branch that is hardest to guess (here: the null belongs to the metric that has no
baseline at all) and keep the other samples computed from reachable inputs.

## Validation patterns

- Path/query binding errors and bean-validation failures both surface as `400 COMMON_003` with a
  descriptive message; throw `ValidationException(COMMON_003, "...")` for cross-field rules
  (e.g. mutually exclusive filters, `end < start`).
- Ownership checks throw `NotFoundException(COMMON_002, "...")` — see principle 3.
- Never `return null` for an empty collection; return an empty list so clients can iterate blindly.

### A constraint on an element type only runs when the collection is `@Valid`

`@NotEmpty List<ItemDto>` validates the list; it does **not** descend into `ItemDto`. Every
constraint declared on the element type — `@NotNull`, `@NotBlank`, `@Size`, `@PositiveOrZero` — is
inert until the field also carries `@Valid`. The annotations still read correctly, the OpenAPI schema
still advertises them, and nothing fails; the request simply arrives with an unvalidated body.

Easy to introduce because the two annotations sit on adjacent lines and only one of them is about the
collection. When a request DTO holds a list or a nested object, put `@Valid` on it and then assert
it — a test that posts a body violating the element's own constraint and expects `400` is the only
thing separating "declared" from "enforced".

The cost of missing it is not a rejected request but a **downstream failure**: in the push path an
unvalidated field reached a multi-row `INSERT`, where a value exceeding the column width failed the
entire statement — one bad session rejected the whole batch, with an error the client could not act
on and reproduced on every retry. Bound request strings to the width of the column they are stored
in, so the failure is a `400` at the boundary instead of a rejected batch deeper in.

## Test recipe

1. Happy path asserting the wrapped envelope (`$.success`, `$.data...`).
2. Each declared failure status at least once, asserting both status and `$.code`.
3. For a new endpoint: one test asserting the contract **shape** (envelope nesting), because the
   frontend codegen depends on it.
4. For a family change (new shared param): one test per sibling proving they all behave the same.
