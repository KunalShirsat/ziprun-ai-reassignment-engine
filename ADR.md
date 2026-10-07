# ADR — ZipRun AI Reassignment Engine

## Context

When a delivery agent becomes unavailable, assigned orders need replacement recommendations without removing operations staff from the decision. The project was built for a five-hour solo hackathon: the design favors a demonstrable, small Spring application and in-process asynchronous work over additional infrastructure.

## Decision Summary

| Area | Chosen approach |
| --- | --- |
| Routing | Spring `RoutingStrategy` implementations called through `RoutingEngine` |
| Strategy selection | Runtime selection between `ruleBased` and `ai` |
| Model access | `LLMGateway` abstraction with `LiteLlmGateway` provider adapter |
| Long-running work | Spring asynchronous event handlers, a dedicated Java 21 virtual-thread executor, and persisted suggestion state |
| Assignment | Human approval; assignment and workload changes in one transaction |
| Persistence | Spring Data JPA with H2 for the demo |

## 1. Routing Strategy Abstraction

**Decision.** `RoutingStrategy` defines recommendation behavior. `RuleBasedRoutingStrategy` ranks available agents by current `activeOrderCount`; `AiRoutingStrategy` requests and validates an AI recommendation and falls back to rule-based routing when needed. `RoutingEngine` is the common entry point for INITIAL and AGENT_OFFLINE decisions and delegates to the active implementation.

**Alternatives considered.** Put routing logic directly in controllers or workflow services; select between implementations using a large conditional; or choose a strategy only once at application startup.

**Why this approach.** Separate Spring-managed implementations keep routing policy out of request and event workflows. A common entry point lets those workflows use the same routing contract and makes another strategy possible without rewriting the callers. Runtime selection also supports comparison and switching during a demo.

**Trade-offs.** There are more types and a small amount of strategy-registration/configuration state than a single conditional would require. The rule-based policy uses workload only; it does not optimize location or travel time.

## 2. Runtime Strategy Switching

**Decision.** `routing.strategy` sets the startup strategy, and `PATCH /routing/strategy` switches the active strategy at runtime between `ruleBased` and `ai`. `GET /routing/strategy` reports the same active value held by `RoutingEngine`; unknown strategy names are rejected.

**Why this approach.** Switching without restart supports experimentation, gives operators a visible demo control, and makes it straightforward to compare AI recommendations with the deterministic rule-based policy. The AI strategy also uses that rule-based policy as its fallback. Additional registered strategies can use the same selection mechanism.

**Alternatives considered.** Require a restart or deployment change to select a strategy, or use configuration only at application startup.

**Trade-offs.** Runtime switching adds a small amount of mutable strategy state and UI/API configuration. A switch affects subsequent routing calls; it does not change recommendations already persisted.

## 3. AI Gateway Separation

**Decision.** Provider HTTP access belongs to `LiteLlmGateway`, behind `LLMGateway`. `PromptBuilder` constructs the trigger-specific prompt. `AiRoutingStrategy` parses and validates the returned model text, selects the candidate recommendation, and invokes the rule-based fallback when necessary. Workflow services call the routing abstraction rather than implementing provider access.

**Alternatives considered.** Call LiteLLM directly from `AiRoutingStrategy`, or put provider-specific HTTP and credential behavior in business services.

**Why this approach.** The gateway owns endpoint, model, headers, credentials, timeouts, and provider response extraction. Keeping those details out of routing policy and business workflows makes the application flow testable with a mocked gateway and avoids coupling workflow code to LiteLLM's transport format.

**Trade-offs.** The gateway interface and adapter add a small layer of indirection. The current adapter is LiteLLM-specific; a different provider would require another implementation and configuration.

## 4. Asynchronous INITIAL Suggestion Generation

**Decision.** `POST /orders/{id}/suggest` persists a `PROCESSING` suggestion, publishes an event containing its ID, and returns HTTP 202. An `@Async("virtualThreadTaskExecutor")` `@TransactionalEventListener` handles it after commit. `SuggestionGenerationService` reloads the row, calls the active strategy with INITIAL context, and updates the same suggestion ID to `PENDING`.

**Alternatives considered.** Wait for the LLM synchronously in the HTTP request, or persist work to an external queue.

**Why this approach.** Synchronous generation would put variable external LLM latency and availability on the request critical path. Persisting the placeholder first isolates that latency from the response and lets the UI show a user-visible PROCESSING state while generation continues.

**Trade-offs.** The in-process asynchronous worker is simple and needs no broker, but work is not durable across a process crash. An unexpected worker failure is logged and can leave the suggestion PROCESSING; there is no persistent queue, automatic retry, or FAILED state.

### Java 21 Virtual Thread Executor

**Decision.** Both asynchronous workflow handlers explicitly use the Spring `virtualThreadTaskExecutor` bean. It adapts an `ExecutorService` created with `Executors.newVirtualThreadPerTaskExecutor()`; Spring manages the underlying executor bean and closes it during application shutdown.

**Why this approach.** The default asynchronous executor is not retained for these workflows: an explicitly qualified executor makes the execution choice clear and gives blocking LLM/HTTP and database waits a lightweight thread-per-task model. This uses Java 21 intentionally for the application's I/O-heavy asynchronous work, rather than as a demonstration-only feature.

**Trade-offs.** Virtual Threads can improve concurrency for blocking workloads; they do not make CPU-bound work faster. A per-task executor does not impose a concurrency limit, so increased simultaneous work can also increase pressure on the LLM provider and database.

## 5. AGENT_OFFLINE Event-Driven Replanning

**Decision.** A newly OFFLINE agent status causes `AgentService` to publish `AgentOfflineEvent`. The `@Async("virtualThreadTaskExecutor")` `ReplanningEventHandler` calls `ReplanningService`, which finds that agent's still-ASSIGNED orders, obtains currently AVAILABLE candidates, invokes the active `RoutingEngine` with `AGENT_OFFLINE`, and persists a pending suggestion. Operations staff decide whether to accept it.

**Alternatives considered.** Perform all replanning synchronously inside `PATCH /agents/{id}/status`; periodically poll for offline agents; or publish events to an external broker.

**Why this approach.** In-process event handling returns the status response without waiting for planning, reacts directly to the status transition, and adds no infrastructure or operational setup—appropriate for the hackathon demo. Per-order runtime failures are logged so the loop can continue with other affected orders. A pending-suggestion check avoids ordinary repeat suggestions.

**Trade-offs.** The event is not durably queued and can be lost on process failure, unlike a persistent broker message. The duplicate check is not an atomic uniqueness guarantee under concurrent events. This design is asynchronous in-process replanning, not a distributed workflow engine.

## 6. AI Failure and Rule-Based Fallback

**Decision.** The AI path accepts a recommendation only after validating the response shape and required fields, confidence range, nonblank values, and that the selected agent is in the current available-candidate list. On an empty response, malformed output, provider error/timeout, or invalid recommendation, `AiRoutingStrategy` logs a sanitized warning and delegates to `RuleBasedRoutingStrategy`.

**Alternatives considered.** Fail the suggestion workflow whenever the model fails, or accept model output without application validation.

**Why this approach.** Reassignment recommendations remain available when an external model is unavailable or returns unusable output, while validation prevents an arbitrary or unavailable agent from being used.

**Trade-offs.** Fallback preserves operational continuity but means a recommendation created while AI mode is selected may be rule-based. The current suggestion contract does not separately expose whether fallback produced an individual recommendation.

## 7. Human-in-the-Loop Approval

**Decision.** Routing creates a suggestion; it does not automatically change the order assignment. The system validates and persists a pending recommendation, then a human accepts or rejects it. Acceptance performs reassignment; rejection leaves the assignment unchanged.

**Alternatives considered.** Automatically apply the highest-ranked recommendation without operator review.

**Why this approach.** Delivery reassignment affects live operations. Keeping the operator in control makes the recommendation reviewable before the assignment changes and fits the dashboard's explicit decision flow.

**Trade-offs.** This adds a human action and delays reassignment compared with full automation. It favors safety and control over an autonomous end-to-end assignment.

## 8. Active Order Count Consistency

**Decision.** On acceptance, `SuggestionService` updates the previous agent's `activeOrderCount`, the recommended agent's `activeOrderCount`, the order assignment, the order status (`REASSIGNED`), and the suggestion status (`ACCEPTED`) within the same transaction. If the old and new agent are the same, counts are unchanged.

**Alternatives considered.** Recompute loads later, or update counts separately from the order assignment.

**Why this approach.** The dashboard and subsequent routing decisions use active counts as workload evidence. Updating the assignment and the corresponding counts together avoids an accepted reassignment leaving the displayed and routing workload out of sync.

**Trade-offs.** The transaction keeps these updates atomic within this application/database, but does not provide distributed coordination for concurrent assignments.

## Testing / Verification

The backend uses unit tests for routing, prompt construction, gateway behavior, and services, plus Spring integration tests that exercise HTTP endpoints and persistence. The AGENT_OFFLINE integration test drives the status and acceptance APIs, uses a mocked `LLMGateway`, and awaits asynchronous suggestion persistence with Awaitility. Gateway tests mock provider HTTP; a real LiteLLM call has been verified separately from the automated suite. The frontend has been verified with a Vite production build; no frontend component-test framework is configured. The latest recorded clean backend suite had 50 passing tests and no failures or errors.

## Deliberate Exclusions

Within the five-hour solo hackathon constraint, the project does not implement:

- A durable external event broker or persistent job queue.
- A distributed workflow engine or advanced autonomous-agent orchestration.
- Production-grade distributed locking or database-enforced atomic duplicate prevention.
- A frontend component-test framework.
- Geospatial or travel-time routing optimization.

These are consciously outside the demo's scope; the implemented in-process handlers, available-agent filtering, rule-based fallback, and human approval cover the demonstration workflow without them.
