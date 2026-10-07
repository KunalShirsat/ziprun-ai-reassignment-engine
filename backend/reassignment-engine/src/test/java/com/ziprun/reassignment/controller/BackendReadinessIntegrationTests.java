package com.ziprun.reassignment.controller;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ziprun.reassignment.ai.LLMGateway;
import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.OrderStatus;
import com.ziprun.reassignment.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.entity.SuggestionStatus;
import com.ziprun.reassignment.entity.TriggerReason;
import com.ziprun.reassignment.config.DemoDataInitializer;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.repository.SuggestionRepository;
import com.ziprun.reassignment.strategy.RoutingEngine;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendReadinessIntegrationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SuggestionRepository suggestionRepository;

    @Autowired
    private DemoDataInitializer demoDataInitializer;

    @Autowired
    private RoutingEngine routingEngine;

    @MockitoBean
    private LLMGateway llmGateway;

    @Test
    void startupSeedIsIdempotentAndCreatesFiveAgentsAndEightAssignedOrdersWithMatchingLoads()
            throws Exception {
        demoDataInitializer.run();

        var seededAgentIds = java.util.Set.of("A1", "A2", "A3", "A4", "A5");
        var seededOrderIds = java.util.Set.of("O1", "O2", "O3", "O4", "O5", "O6", "O7", "O8");
        var seededAgents = agentRepository.findAll().stream()
                .filter(agent -> seededAgentIds.contains(agent.getId()))
                .toList();
        var seededOrders = orderRepository.findAll().stream()
                .filter(order -> seededOrderIds.contains(order.getId()))
                .toList();

        org.junit.jupiter.api.Assertions.assertEquals(5, seededAgents.size());
        org.junit.jupiter.api.Assertions.assertEquals(8, seededOrders.size());
        org.junit.jupiter.api.Assertions.assertTrue(seededOrders.stream()
                .allMatch(order -> order.getStatus() == OrderStatus.ASSIGNED));
        for (Agent agent : seededAgents) {
            long assignedOrderCount = orderRepository.findByAssignedAgent_Id(agent.getId()).stream()
                    .filter(order -> seededOrderIds.contains(order.getId()))
                    .count();
            org.junit.jupiter.api.Assertions.assertEquals(agent.getActiveOrderCount(), assignedOrderCount);
        }
        org.junit.jupiter.api.Assertions.assertEquals(
                3,
                seededAgents.stream().filter(agent -> agent.getStatus() == AgentStatus.AVAILABLE).count());
    }

    @Test
    void suggestionPersistsReasoningLongerThan255Characters() {
        Agent assignedAgent = agentRepository.findById("A1").orElseThrow();
        Agent recommendedAgent = agentRepository.findById("A5").orElseThrow();
        Order order = order("IT-ORDER-LONG-REASONING", assignedAgent);
        orderRepository.save(order);

        String reasoning = "Detailed AI reasoning. ".repeat(30);
        ReassignmentSuggestion suggestion = suggestion(order, recommendedAgent);
        suggestion.setReasoning(reasoning);

        ReassignmentSuggestion saved = suggestionRepository.saveAndFlush(suggestion);
        ReassignmentSuggestion reloaded = suggestionRepository.findById(saved.getId()).orElseThrow();

        org.junit.jupiter.api.Assertions.assertTrue(reasoning.length() > 255);
        org.junit.jupiter.api.Assertions.assertEquals(reasoning, reloaded.getReasoning());
    }

    @Test
    void createOrderAndListEndpointsRemainAvailable() throws Exception {
        HttpResponse<String> createdResponse = send(
                "POST",
                "/orders",
                "{\"id\":\"IT-ORDER-CREATE\",\"description\":\"Created over HTTP\","
                        + "\"assignedAgentId\":\"A5\"}");
        org.junit.jupiter.api.Assertions.assertEquals(201, createdResponse.statusCode());
        JsonNode createdBody = objectMapper.readTree(createdResponse.body());
        org.junit.jupiter.api.Assertions.assertEquals("IT-ORDER-CREATE", createdBody.path("id").asText());
        org.junit.jupiter.api.Assertions.assertEquals("ASSIGNED", createdBody.path("status").asText());

        HttpResponse<String> agentsResponse = send("GET", "/agents", null);
        HttpResponse<String> ordersResponse = send("GET", "/orders", null);
        org.junit.jupiter.api.Assertions.assertEquals(200, agentsResponse.statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(objectMapper.readTree(agentsResponse.body()).isArray());
        org.junit.jupiter.api.Assertions.assertEquals(200, ordersResponse.statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(objectMapper.readTree(ordersResponse.body()).isArray());
    }

    @Test
    void suggestEndpointReturnsProcessingSuggestionAndLeavesOrderAssigned() throws Exception {
        Agent assignedAgent = agent("IT-ASSIGNED", AgentStatus.BUSY);
        Agent availableAgent = agent("IT-AVAILABLE", AgentStatus.AVAILABLE);
        agentRepository.save(assignedAgent);
        agentRepository.save(availableAgent);
        Order order = order("IT-ORDER-SUGGEST", assignedAgent);
        orderRepository.save(order);

        HttpResponse<String> response = send("POST", "/orders/" + order.getId() + "/suggest", null);
        JsonNode body = objectMapper.readTree(response.body());

        org.junit.jupiter.api.Assertions.assertEquals(202, response.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(order.getId(), body.path("orderId").asText());
        org.junit.jupiter.api.Assertions.assertTrue(body.path("recommendedAgentId").isNull());
        org.junit.jupiter.api.Assertions.assertEquals("PROCESSING", body.path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals("INITIAL", body.path("triggerReason").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0.0, body.path("confidence").asDouble());
        org.junit.jupiter.api.Assertions.assertEquals("Generating recommendation.", body.path("reasoning").asText());

        ReassignmentSuggestion persisted = suggestionRepository.findById(
                java.util.UUID.fromString(body.path("id").asText())).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(order.getId(), persisted.getOrder().getId());
        org.junit.jupiter.api.Assertions.assertEquals(TriggerReason.INITIAL, persisted.getTriggerReason());

        Order unchangedOrder = orderRepository.findById(order.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.ASSIGNED, unchangedOrder.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(assignedAgent.getId(),
                unchangedOrder.getAssignedAgent().getId());
    }

    @Test
    void initialSuggestionHttpRequestReturnsWhileMockLlmIsStillRunning() throws Exception {
        Agent assignedAgent = agentRepository.findById("A1").orElseThrow();
        Order order = order("IT-ORDER-ASYNC-SUGGEST", assignedAgent);
        orderRepository.save(order);

        CountDownLatch llmStarted = new CountDownLatch(1);
        CountDownLatch releaseLlm = new CountDownLatch(1);
        when(llmGateway.generate(anyString())).thenAnswer(invocation -> {
            llmStarted.countDown();
            if (!releaseLlm.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Mock LLM release timed out");
            }
            return """
                    {"agentId":"A5","confidence":0.84,"reasoning":"Mock AI recommendation."}
                    """;
        });
        routingEngine.switchStrategy("ai");

        CompletableFuture<HttpResponse<String>> request = CompletableFuture.supplyAsync(() -> {
            try {
                return send("POST", "/orders/" + order.getId() + "/suggest", null);
            } catch (IOException | InterruptedException exception) {
                throw new IllegalStateException(exception);
            }
        });

        try {
            org.junit.jupiter.api.Assertions.assertTrue(llmStarted.await(5, TimeUnit.SECONDS));
            HttpResponse<String> response = request.get(3, TimeUnit.SECONDS);
            JsonNode body = objectMapper.readTree(response.body());
            java.util.UUID suggestionId = java.util.UUID.fromString(body.path("id").asText());

            org.junit.jupiter.api.Assertions.assertEquals(202, response.statusCode());
            org.junit.jupiter.api.Assertions.assertEquals("PROCESSING", body.path("status").asText());
            org.junit.jupiter.api.Assertions.assertEquals(
                    SuggestionStatus.PROCESSING,
                    suggestionRepository.findById(suggestionId).orElseThrow().getStatus());

            releaseLlm.countDown();
            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                ReassignmentSuggestion generated = suggestionRepository.findById(suggestionId).orElseThrow();
                org.junit.jupiter.api.Assertions.assertEquals(SuggestionStatus.PENDING, generated.getStatus());
                org.junit.jupiter.api.Assertions.assertEquals("A5", generated.getRecommendedAgent().getId());
                org.junit.jupiter.api.Assertions.assertEquals(0.84, generated.getConfidence());
                org.junit.jupiter.api.Assertions.assertEquals("Mock AI recommendation.", generated.getReasoning());
            });
            org.junit.jupiter.api.Assertions.assertTrue(suggestionRepository.findById(suggestionId).isPresent());
        } finally {
            releaseLlm.countDown();
            routingEngine.switchStrategy("ruleBased");
        }
    }

    @Test
    void offlineStatusEndpointReturnsUpdatedAgentResponse() throws Exception {
        Agent agent = agent("IT-OFFLINE-AGENT", AgentStatus.AVAILABLE);
        agentRepository.save(agent);

        HttpResponse<String> response = send(
                "PATCH",
                "/agents/" + agent.getId() + "/status",
                "{\"status\":\"OFFLINE\"}");
        JsonNode body = objectMapper.readTree(response.body());

        org.junit.jupiter.api.Assertions.assertEquals(200, response.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(agent.getId(), body.path("id").asText());
        org.junit.jupiter.api.Assertions.assertEquals("OFFLINE", body.path("status").asText());

        org.junit.jupiter.api.Assertions.assertEquals(
                AgentStatus.OFFLINE,
                agentRepository.findById(agent.getId()).orElseThrow().getStatus());
    }

    @Test
    void acceptingSuggestionReassignsOrderAndRejectingSuggestionLeavesAssignmentUnchanged() throws Exception {
        Agent originalAgent = agent("IT-ORIGINAL", AgentStatus.BUSY);
        Agent recommendedAgent = agent("IT-RECOMMENDED", AgentStatus.AVAILABLE);
        originalAgent.setActiveOrderCount(2);
        recommendedAgent.setActiveOrderCount(3);
        agentRepository.save(originalAgent);
        agentRepository.save(recommendedAgent);

        Order acceptedOrder = order("IT-ORDER-ACCEPT", originalAgent);
        Order rejectedOrder = order("IT-ORDER-REJECT", originalAgent);
        orderRepository.save(acceptedOrder);
        orderRepository.save(rejectedOrder);

        ReassignmentSuggestion acceptedSuggestion = suggestion(acceptedOrder, recommendedAgent);
        ReassignmentSuggestion rejectedSuggestion = suggestion(rejectedOrder, recommendedAgent);
        suggestionRepository.save(acceptedSuggestion);
        suggestionRepository.save(rejectedSuggestion);

        HttpResponse<String> acceptedResponse = send(
                "PATCH",
                "/suggestions/" + acceptedSuggestion.getId(),
                "{\"status\":\"ACCEPTED\"}");
        org.junit.jupiter.api.Assertions.assertEquals(200, acceptedResponse.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                "ACCEPTED",
                objectMapper.readTree(acceptedResponse.body()).path("status").asText());

        HttpResponse<String> rejectedResponse = send(
                "PATCH",
                "/suggestions/" + rejectedSuggestion.getId(),
                "{\"status\":\"REJECTED\"}");
        org.junit.jupiter.api.Assertions.assertEquals(200, rejectedResponse.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                "REJECTED",
                objectMapper.readTree(rejectedResponse.body()).path("status").asText());

        Order acceptedResult = orderRepository.findById(acceptedOrder.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.REASSIGNED, acceptedResult.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(recommendedAgent.getId(),
                acceptedResult.getAssignedAgent().getId());
        org.junit.jupiter.api.Assertions.assertEquals(
                1, agentRepository.findById(originalAgent.getId()).orElseThrow().getActiveOrderCount());
        org.junit.jupiter.api.Assertions.assertEquals(
                4, agentRepository.findById(recommendedAgent.getId()).orElseThrow().getActiveOrderCount());

        Order rejectedResult = orderRepository.findById(rejectedOrder.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.ASSIGNED, rejectedResult.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(originalAgent.getId(),
                rejectedResult.getAssignedAgent().getId());
        org.junit.jupiter.api.Assertions.assertEquals(
                1, agentRepository.findById(originalAgent.getId()).orElseThrow().getActiveOrderCount());
        org.junit.jupiter.api.Assertions.assertEquals(
                4, agentRepository.findById(recommendedAgent.getId()).orElseThrow().getActiveOrderCount());
    }

    @Test
    void acceptingAgentOfflineSuggestionUpdatesBothAgentCounts() throws Exception {
        Agent originalAgent = agent("IT-OFFLINE-ORIGINAL", AgentStatus.OFFLINE);
        Agent recommendedAgent = agent("IT-OFFLINE-RECOMMENDED", AgentStatus.AVAILABLE);
        originalAgent.setActiveOrderCount(4);
        recommendedAgent.setActiveOrderCount(2);
        agentRepository.save(originalAgent);
        agentRepository.save(recommendedAgent);
        Order order = order("IT-ORDER-OFFLINE-ACCEPT", originalAgent);
        orderRepository.save(order);
        ReassignmentSuggestion suggestion = suggestion(order, recommendedAgent);
        suggestion.setTriggerReason(TriggerReason.AGENT_OFFLINE);
        suggestionRepository.save(suggestion);

        HttpResponse<String> response = send(
                "PATCH",
                "/suggestions/" + suggestion.getId(),
                "{\"status\":\"ACCEPTED\"}");

        org.junit.jupiter.api.Assertions.assertEquals(200, response.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                3, agentRepository.findById(originalAgent.getId()).orElseThrow().getActiveOrderCount());
        org.junit.jupiter.api.Assertions.assertEquals(
                3, agentRepository.findById(recommendedAgent.getId()).orElseThrow().getActiveOrderCount());
        Order reassignedOrder = orderRepository.findById(order.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.REASSIGNED, reassignedOrder.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                recommendedAgent.getId(), reassignedOrder.getAssignedAgent().getId());
    }

    @Test
    void acceptingSameAgentSuggestionDoesNotChangeAgentCount() throws Exception {
        Agent agent = agent("IT-SAME-AGENT", AgentStatus.AVAILABLE);
        agent.setActiveOrderCount(7);
        agentRepository.save(agent);
        Order order = order("IT-ORDER-SAME-AGENT", agent);
        orderRepository.save(order);
        ReassignmentSuggestion suggestion = suggestion(order, agent);
        suggestionRepository.save(suggestion);

        HttpResponse<String> response = send(
                "PATCH",
                "/suggestions/" + suggestion.getId(),
                "{\"status\":\"ACCEPTED\"}");

        org.junit.jupiter.api.Assertions.assertEquals(200, response.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                7, agentRepository.findById(agent.getId()).orElseThrow().getActiveOrderCount());
        Order reassignedOrder = orderRepository.findById(order.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.REASSIGNED, reassignedOrder.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(agent.getId(), reassignedOrder.getAssignedAgent().getId());
    }

    @Test
    void processingSuggestionCannotBeAcceptedOrRejected() throws Exception {
        Agent assignedAgent = agent("IT-PROCESSING-ASSIGNED", AgentStatus.BUSY);
        agentRepository.save(assignedAgent);
        Order processingOrder = order("IT-ORDER-PROCESSING-PATCH", assignedAgent);
        orderRepository.save(processingOrder);
        ReassignmentSuggestion processingSuggestion = suggestion(processingOrder, null);
        processingSuggestion.setRecommendedAgent(null);
        processingSuggestion.setStatus(SuggestionStatus.PROCESSING);
        processingSuggestion.setReasoning("Generating recommendation.");
        suggestionRepository.save(processingSuggestion);

        HttpResponse<String> response = send(
                "PATCH",
                "/suggestions/" + processingSuggestion.getId(),
                "{\"status\":\"ACCEPTED\"}");

        org.junit.jupiter.api.Assertions.assertEquals(409, response.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                SuggestionStatus.PROCESSING,
                suggestionRepository.findById(processingSuggestion.getId()).orElseThrow().getStatus());
    }

    @Test
    void getSuggestionsReturnsEmptyAllAndStatusFilteredSuggestionResponses() throws Exception {
        suggestionRepository.deleteAll();

        HttpResponse<String> emptyResponse = send("GET", "/suggestions", null);
        org.junit.jupiter.api.Assertions.assertEquals(200, emptyResponse.statusCode());
        JsonNode emptySuggestions = objectMapper.readTree(emptyResponse.body());
        org.junit.jupiter.api.Assertions.assertTrue(emptySuggestions.isArray());
        org.junit.jupiter.api.Assertions.assertEquals(0, emptySuggestions.size());

        Agent recommendedAgent = agentRepository.findById("A5").orElseThrow();
        Agent assignedAgent = agentRepository.findById("A1").orElseThrow();
        Order pendingOrder = order("IT-ORDER-SUGGEST-PENDING", assignedAgent);
        Order acceptedOrder = order("IT-ORDER-SUGGEST-ACCEPTED", assignedAgent);
        Order rejectedOrder = order("IT-ORDER-SUGGEST-REJECTED", assignedAgent);
        orderRepository.saveAll(java.util.List.of(pendingOrder, acceptedOrder, rejectedOrder));

        ReassignmentSuggestion pending = suggestion(pendingOrder, recommendedAgent);
        ReassignmentSuggestion accepted = suggestion(acceptedOrder, recommendedAgent);
        accepted.setStatus(SuggestionStatus.ACCEPTED);
        ReassignmentSuggestion rejected = suggestion(rejectedOrder, recommendedAgent);
        rejected.setStatus(SuggestionStatus.REJECTED);
        suggestionRepository.saveAll(java.util.List.of(pending, accepted, rejected));

        HttpResponse<String> allResponse = send("GET", "/suggestions", null);
        JsonNode allSuggestions = objectMapper.readTree(allResponse.body());
        org.junit.jupiter.api.Assertions.assertEquals(200, allResponse.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(3, allSuggestions.size());
        JsonNode suggestionDto = allSuggestions.get(0);
        org.junit.jupiter.api.Assertions.assertTrue(suggestionDto.has("orderId"));
        org.junit.jupiter.api.Assertions.assertTrue(suggestionDto.has("recommendedAgentId"));
        org.junit.jupiter.api.Assertions.assertFalse(suggestionDto.has("order"));
        org.junit.jupiter.api.Assertions.assertFalse(suggestionDto.has("recommendedAgent"));

        HttpResponse<String> pendingResponse = send("GET", "/suggestions?status=PENDING", null);
        JsonNode pendingSuggestions = objectMapper.readTree(pendingResponse.body());
        org.junit.jupiter.api.Assertions.assertEquals(200, pendingResponse.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(1, pendingSuggestions.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                "PENDING",
                pendingSuggestions.get(0).path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                pending.getId().toString(),
                pendingSuggestions.get(0).path("id").asText());
    }

    private HttpResponse<String> send(String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Agent agent(String id, AgentStatus status) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(id);
        agent.setStatus(status);
        agent.setActiveOrderCount(0);
        return agent;
    }

    private Order order(String id, Agent assignedAgent) {
        Order order = new Order();
        order.setId(id);
        order.setDescription("Integration test order");
        order.setAssignedAgent(assignedAgent);
        order.setStatus(OrderStatus.ASSIGNED);
        return order;
    }

    private ReassignmentSuggestion suggestion(Order order, Agent recommendedAgent) {
        ReassignmentSuggestion suggestion = new ReassignmentSuggestion();
        suggestion.setOrder(order);
        suggestion.setRecommendedAgent(recommendedAgent);
        suggestion.setConfidence(0.9);
        suggestion.setReasoning("Integration test recommendation");
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setTriggerReason(TriggerReason.INITIAL);
        return suggestion;
    }
}
