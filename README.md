# ZipRun AI Reassignment Engine

ZipRun is a hackathon operations dashboard for recommending replacement delivery agents when assignments are at risk. Operations reviews recommendations and must accept one before an order is reassigned.

## Architecture

- **React frontend:** Displays agents, orders, and active suggestions; polls the backend every four seconds and lets operations request or decide on suggestions.
- **Spring Boot backend:** Exposes REST APIs and uses Spring Data JPA with an in-memory H2 database for the local demo.
- **RoutingStrategy / RoutingEngine:** Strategies implement one recommendation contract. `RoutingEngine` resolves the currently active strategy for each recommendation.
- **RuleBasedRoutingStrategy:** Ranks available agents by `activeOrderCount`, lowest first.
- **AiRoutingStrategy:** Builds trigger-specific prompts, parses the model’s JSON, validates the recommendation, and falls back to rule-based routing if the AI result cannot be used.
- **LLMGateway / LiteLlmGateway:** Separates provider access from routing logic and sends OpenAI-compatible chat-completions requests to LiteLLM.
- **INITIAL generation:** Persists a PROCESSING suggestion, then uses an after-commit asynchronous event to run the active strategy and update the same row to PENDING.
- **Agent-offline replanning:** A newly OFFLINE agent publishes an event. An asynchronous handler finds its still-ASSIGNED orders and creates AGENT_OFFLINE suggestions using the same RoutingEngine.

## Routing strategy

The startup default is `ruleBased` (`routing.strategy=ruleBased`). The runtime endpoint also accepts the exact strategy names **`ruleBased`** and **`ai`**:

```http
PATCH /routing/strategy
Content-Type: application/json

{"strategy":"ai"}
```

Response:

```json
{"strategy":"ai"}
```

Use `{"strategy":"ruleBased"}` to switch back. Switching takes effect immediately for subsequent recommendations; it does not require an application restart. Unknown strategy names are rejected with HTTP 400. AI mode requires `LITELLM_API_KEY`; when an AI call or its response fails validation, rule-based routing is used instead.

## Suggestion lifecycle and routing

Suggestions move through:

```text
PROCESSING -> PENDING -> ACCEPTED
                       -> REJECTED
```

`PROCESSING` means generation is underway and the suggestion is not actionable. `PENDING` means a recommendation is ready for operations review.

### INITIAL recommendation

`POST /orders/{id}/suggest` returns **202 Accepted** with a persisted PROCESSING suggestion. It does not wait for the LLM. After the database transaction commits, the asynchronous worker reloads the suggestion by ID, calls the active routing strategy, and updates that **same suggestion** to PENDING with its recommended agent, confidence, reasoning, and `triggerReason: "INITIAL"`. A repeated request returns an existing PROCESSING or PENDING INITIAL suggestion for that order rather than creating another active one.

The dashboard polls suggestions and displays PROCESSING as “Generating” without approval buttons. Once the row becomes PENDING, polling displays the recommendation and its agent, confidence, and reasoning.

### Agent goes OFFLINE

`PATCH /agents/{id}/status` updates the agent and returns its response without waiting for replanning. When the status newly changes to OFFLINE, an in-process asynchronous event handler finds orders still assigned to that agent with status ASSIGNED. For each order, it checks for an existing PENDING AGENT_OFFLINE suggestion, routes through `RoutingEngine`, and creates a PENDING suggestion. This is an application-level check; it is not a database uniqueness guarantee for simultaneous events. A routing/runtime failure for one order is logged and does not stop the loop for other orders.

AGENT_OFFLINE suggestions require operations approval just like INITIAL suggestions. Accepting a suggestion marks it ACCEPTED, changes the order to REASSIGNED, assigns its recommended agent, and updates both agents’ `activeOrderCount` values in the same transaction (unless both agents are the same). Rejecting changes the suggestion to REJECTED and leaves the order assignment and counts unchanged.

## AI and LiteLLM configuration

The configured gateway uses:

- Base URL: `https://litellm-qc.zycus.net`
- Path: `/v1/chat/completions`
- Model: `qwen-cursor`
- Product header: `PC1`
- Connect timeout: 5 seconds; read timeout: 20 seconds

Set `LITELLM_API_KEY` in the backend process environment to enable AI requests. The property resolves this variable and is empty if it is not set. Never put a real credential in source control or documentation. AI routing sends the order description and the available agents’ IDs, names, and current active-order counts. The INITIAL and AGENT_OFFLINE prompts explain their different contexts.

The gateway handles the provider-specific HTTP request and returns model text. `AiRoutingStrategy` parses JSON containing `agentId`, `confidence`, and `reasoning`, requires the agent ID to match an available agent, validates finite confidence in the range 0–1 and nonblank reasoning, and falls back to rule-based routing on provider/runtime errors, timeouts, empty or malformed output, or invalid recommendations. Fallback logs include the order, trigger, and sanitized cause type—not the prompt, response, or credential.

## REST API

Enum values are uppercase. Responses use DTOs rather than returning JPA entities.

| Method and path | Behavior |
| --- | --- |
| `POST /orders` | Create an ASSIGNED order for an existing agent; returns 201. |
| `GET /orders` | List orders; optional `?status=` filters by order status. |
| `POST /orders/{id}/suggest` | Create or return an active INITIAL suggestion; returns 202 with PROCESSING while generation runs. |
| `GET /agents` | List agents with status and `activeOrderCount`. |
| `PATCH /agents/{id}/status` | Update status; newly setting OFFLINE starts asynchronous replanning. |
| `PATCH /routing/strategy` | Switch active strategy to `ai` or `ruleBased`; returns `{"strategy":"..."}`. |
| `GET /suggestions` | List all suggestions. |
| `GET /suggestions?status=PROCESSING` | Filter suggestions by any `SuggestionStatus`: PROCESSING, PENDING, ACCEPTED, or REJECTED. |
| `PATCH /suggestions/{id}` | Accept or reject a PENDING suggestion. PROCESSING decisions return 409. |

Create-order request:

```json
{
  "id": "O9",
  "description": "Parcel delivery to North Street",
  "assignedAgentId": "A3"
}
```

INITIAL suggestion response (the UUID is generated and persisted before the response):

```json
{
  "id": "2d6cb19b-c892-4f4e-bf14-0fb28b362b49",
  "orderId": "O1",
  "recommendedAgentId": null,
  "recommendedAgentName": null,
  "confidence": 0.0,
  "reasoning": "Generating recommendation.",
  "status": "PROCESSING",
  "triggerReason": "INITIAL"
}
```

After generation, the same ID is returned by `GET /suggestions` with status PENDING and populated recommendation fields. `PATCH /suggestions/{id}` accepts `{"status":"ACCEPTED"}` or `{"status":"REJECTED"}`.

## Demo data

On an empty database the initializer creates five agents and eight ASSIGNED orders; it creates no suggestions. Initial workload counts match the seeded assignments:

| Agent ID | Name | Status | Active orders | Seeded orders |
| --- | --- | --- | ---: | --- |
| A1 | Maya Chen | BUSY | 2 | O1, O2 |
| A2 | Noah Patel | BUSY | 2 | O3, O4 |
| A3 | Sophia Brooks | AVAILABLE | 2 | O5, O6 |
| A4 | Liam Rivera | AVAILABLE | 1 | O7 |
| A5 | Ava Thompson | AVAILABLE | 1 | O8 |

The initializer skips seeding when either A1 or O1 already exists.

## Run locally

Prerequisites: Java 21 and Node.js/npm.

Start the backend:

```powershell
cd backend/reassignment-engine
.\mvnw.cmd spring-boot:run
```

The backend runs at `http://localhost:8080`. Rule-based routing is the startup default. To use AI, set the environment variable before starting the backend and switch the active strategy through `PATCH /routing/strategy`:

```powershell
$env:LITELLM_API_KEY = "<your-key>"
```

Start the frontend in another terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/backend-api` to the backend on port 8080 during development. The dashboard polls agents, orders, and PROCESSING/PENDING suggestions every four seconds; an error banner offers Retry when a dashboard request fails.

## Testing and verification

The latest recorded backend run completed with **44 tests passing, 0 failures, and 0 errors** (`mvnw.cmd clean test`). The frontend production build has also been verified with `npm run build`.

## Deliberate exclusions and limitations

- No authentication or authorization.
- H2 in-memory storage is for the local demo, not production persistence.
- No WebSockets/SSE or push notifications; the dashboard uses polling.
- Routing does not use geospatial distance or travel-time optimization.
- Offline events and async work run in-process; there is no durable message broker.
- The duplicate check is application-level and is not atomic across simultaneous concurrent requests.
- Unexpected async INITIAL generation failures are logged; the current lifecycle has no FAILED status or automatic retry.
