# ADR — AI Reassignment Engine Architecture

## Context

Delivery orders can be put at risk when an assigned agent goes offline. ZipRun identifies affected orders, recommends available replacement agents, and leaves the final assignment decision to operations.

The project targets a five-hour hackathon demo. It favors small interfaces, an in-memory database, asynchronous work without additional infrastructure, and a human approval step over production-scale optimization and operations.

## Decision 1 — Routing strategy abstraction

Routing behavior is represented by the `RoutingStrategy` interface and invoked through `RoutingEngine`:

```text
RoutingEngine
  -> current strategy
       -> RuleBasedRoutingStrategy
       -> AiRoutingStrategy
```

Both INITIAL suggestion generation and AGENT_OFFLINE replanning call `RoutingEngine` with the trigger context. This keeps strategy choice out of the workflows and lets another Spring-managed implementation be added under a registered strategy name.

The rule-based implementation orders available agents by `activeOrderCount`, lowest first. It is intentionally simple and does not optimize geography or travel time.

## Decision 2 — Runtime strategy selection

`RoutingEngine` receives Spring-managed strategies by name. `routing.strategy=ruleBased` sets the startup default. The active strategy can also be changed at runtime using `PATCH /routing/strategy` with `{"strategy":"ai"}` or `{"strategy":"ruleBased"}`. The controller rejects unknown names with HTTP 400. The new selection applies to subsequent recommendations and does not require restart.

## Decision 3 — AI gateway and credential configuration

`LLMGateway` separates model/provider access from routing and validation. `LiteLlmGateway` implements the OpenAI-compatible LiteLLM chat-completions call using configured endpoint, model, product header, and HTTP timeouts. It returns only the model’s textual content; `AiRoutingStrategy` owns parsing and validation.

The API credential is resolved from the `LITELLM_API_KEY` environment variable through Spring configuration. It is not included in the project configuration as a literal and must not be committed or logged. AI is not selected by default; the default strategy is `ruleBased`.

## Decision 4 — AI validation and rule-based fallback

AI recommendations are used only when the response contains a nonblank `agentId`, a finite `confidence` from 0 through 1, and nonblank `reasoning`, and the selected ID belongs to the available-agent list.

If the credential is missing, the provider request fails or times out, the response is empty or malformed, or the recommendation fails validation, `AiRoutingStrategy` logs a sanitized warning and falls back to `RuleBasedRoutingStrategy`. Logs do not include the credential, prompt, or full model response.

## Decision 5 — Asynchronous INITIAL suggestion generation

`POST /orders/{id}/suggest` persists a suggestion with status PROCESSING, neutral confidence, no recommended agent, and processing text, then returns HTTP 202. It does not wait for model generation.

The request publishes an event containing the suggestion ID. An `@Async @TransactionalEventListener(AFTER_COMMIT)` handler runs only after the placeholder transaction commits. The worker reloads the row in its own transaction, invokes the currently active strategy with INITIAL context, and updates that same row to PENDING with the recommendation fields. This avoids passing a managed JPA entity to the asynchronous thread. A PROCESSING suggestion cannot be accepted or rejected.

AI failures handled by the strategy’s fallback still produce a PENDING rule-based recommendation. An unexpected worker failure is safely logged; there is no FAILED status or automatic retry, so such a row may remain PROCESSING.

## Decision 6 — Asynchronous AGENT_OFFLINE replanning

When an agent newly transitions to OFFLINE, `AgentService` saves the status and publishes an `AgentOfflineEvent`. The `@Async` event handler invokes `ReplanningService`, which finds orders still ASSIGNED to that agent. Each order is routed through `RoutingEngine` with AGENT_OFFLINE context and produces a PENDING AGENT_OFFLINE suggestion. The status PATCH does not wait for this work.

The service checks for an existing pending AGENT_OFFLINE suggestion before creating one and handles an order’s runtime failure without stopping the remaining loop. This check is not a database uniqueness guarantee under simultaneous concurrent events. Routing through AI retains the same rule-based fallback.

## Decision 7 — Prompt context

INITIAL and AGENT_OFFLINE recommendations have different contexts and use separate prompt-building paths. Prompts include order ID and description plus available agents’ IDs, names, and active-order counts. Suggestions persist whether the trigger was INITIAL or AGENT_OFFLINE.

## Decision 8 — Persistence and assignment consistency

Spring Data JPA persists agents, orders, and suggestions. H2 in-memory storage keeps local setup simple and is not intended for production.

Suggestions use PROCESSING, PENDING, ACCEPTED, and REJECTED statuses. A recommendation does not change an order until accepted. Acceptance updates the suggestion, changes the order’s assigned agent and status to REASSIGNED, and adjusts the previous and recommended agents’ `activeOrderCount` values within the same transaction. If the two agents are the same, the count is not changed. Rejection leaves the order assignment and counts unchanged.

Startup demo data provides five agents and eight ASSIGNED orders with matching initial load counts and creates no suggestions. The initializer skips seeding when either A1 or O1 already exists.

## Decision 9 — Frontend polling and human approval

The React dashboard polls agents, orders, and PROCESSING/PENDING suggestions every four seconds. PROCESSING suggestions are shown as generating and have no decision buttons; PENDING suggestions show the recommendation, confidence, reasoning, and trigger and allow accept/reject actions. Polling was chosen over WebSockets or Server-Sent Events to avoid additional infrastructure.

Human approval is required for both initial and offline recommendations:

```text
PROCESSING -> PENDING -> ACCEPTED -> order becomes REASSIGNED
                      -> REJECTED -> assignment unchanged
```

## Deliberate exclusions

The implementation intentionally does not include:

- Authentication or authorization
- A production database or durable event/message broker
- WebSockets, Server-Sent Events, or push notifications
- Geospatial or travel-time routing optimization
- Sophisticated agent memory
- Automatic retries or a FAILED state for unexpected INITIAL worker failures
- Database-enforced uniqueness for active suggestions under concurrent requests

These exclusions keep the demo focused on recommendations, asynchronous replanning, and operations approval.

## Consequences

### Positive

- Callers use one routing abstraction and can switch between registered strategies at runtime.
- INITIAL LLM generation does not block the HTTP request, and its persisted row is updated in place.
- AI output is validated before use; rule-based routing preserves a recommendation path when the AI cannot provide a valid result.
- Offline replanning is asynchronous and isolates failures by order.
- Human approval controls reassignment, and agent workload counts stay consistent with accepted assignment changes.

### Trade-offs

- Rule-based routing uses workload only.
- Polling introduces up to a short delay before updates appear.
- In-process asynchronous events are not durable across application failure.
- A worker failure outside the normal AI fallback is logged but can leave a suggestion PROCESSING.
- The duplicate check is not atomic under concurrent requests.
- H2 is suitable for this local demo, not production persistence.
- AI requires an externally supplied `LITELLM_API_KEY` and is not selected by default.
