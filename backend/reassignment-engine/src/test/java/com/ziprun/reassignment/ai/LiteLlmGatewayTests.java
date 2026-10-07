package com.ziprun.reassignment.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class LiteLlmGatewayTests {

    private static final String API_KEY = "test-secret";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private LiteLlmGateway gateway;
    private AtomicReference<String> requestBody;
    private AtomicReference<String> authorization;
    private AtomicReference<String> product;

    @BeforeEach
    void startMockEndpoint() throws IOException {
        requestBody = new AtomicReference<>();
        authorization = new AtomicReference<>();
        product = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            product.set(exchange.getRequestHeaders().getFirst("product"));
            byte[] response = """
                    {"choices":[{"message":{"content":"{\\"agentId\\":\\"A2\\",\\"confidence\\":0.87,\\"reasoning\\":\\"A2 is suitable.\\"}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        gateway = new LiteLlmGateway(
                RestClient.builder(),
                "http://localhost:" + server.getAddress().getPort(),
                "/v1/chat/completions",
                "qwen-cursor",
                "PC1",
                API_KEY,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }

    @AfterEach
    void stopMockEndpoint() {
        server.stop(0);
    }

    @Test
    void sendsOpenAiCompatibleRequestAndReturnsOnlyMessageContent() throws Exception {
        String prompt = "Recommend an agent for order O1";

        String result = gateway.generate(prompt);

        JsonNode request = objectMapper.readTree(requestBody.get());
        assertEquals("qwen-cursor", request.path("model").asText());
        assertEquals("user", request.path("messages").get(0).path("role").asText());
        assertEquals(prompt, request.path("messages").get(0).path("content").asText());
        assertEquals("Bearer " + API_KEY, authorization.get());
        assertEquals("PC1", product.get());
        JsonNode modelContent = objectMapper.readTree(result);
        assertEquals("A2", modelContent.path("agentId").asText());
        assertEquals(0.87, modelContent.path("confidence").asDouble());
        assertEquals("A2 is suitable.", modelContent.path("reasoning").asText());
    }

    @Test
    void missingApiKeyFailsWithoutMakingRequest() {
        LiteLlmGateway unconfiguredGateway = new LiteLlmGateway(
                RestClient.builder(),
                "http://localhost:" + server.getAddress().getPort(),
                "/v1/chat/completions",
                "qwen-cursor",
                "PC1",
                "",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> unconfiguredGateway.generate("prompt"));

        assertEquals("LiteLLM API key is not configured", exception.getMessage());
        assertTrue(requestBody.get() == null);
    }

    @Test
    void failedRequestDoesNotExposeApiKeyInException() throws IOException {
        server.removeContext("/v1/chat/completions");
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = "failed".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> gateway.generate("prompt"));

        assertEquals("LiteLLM request failed", exception.getMessage());
        assertFalse(exception.getMessage().contains(API_KEY));
    }
}
