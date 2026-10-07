package com.ziprun.reassignment.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.ziprun.reassignment.ai.LLMGateway;
import com.ziprun.reassignment.ai.PromptBuilder;
import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.RecommendationSource;
import com.ziprun.reassignment.entity.TriggerReason;

class AiRoutingStrategyTests {

    private final Agent lowLoadAgent = agent("A1", "Agent One", 1);
    private final Agent otherAgent = agent("A2", "Agent Two", 3);
    private final Order order = order("O1", "Deliver a package");

    @Test
    void validAiResponseReturnsRequestedAvailableAgent() {
        AiRoutingStrategy strategy = strategy(prompt -> """
                {"agentId":"A2","confidence":0.87,"reasoning":"A2 is suitable for this order."}
                """);

        List<RoutingRecommendation> recommendations =
                strategy.recommend(order, List.of(lowLoadAgent, otherAgent));

        assertEquals(1, recommendations.size());
        assertEquals(otherAgent, recommendations.get(0).agent());
        assertEquals(0.87, recommendations.get(0).confidence());
        assertEquals("A2 is suitable for this order.", recommendations.get(0).reasoning());
        assertEquals(RecommendationSource.AI, recommendations.get(0).source());
    }

    @Test
    void invalidAgentIdFallsBackToRuleBasedRouting() {
        assertFallsBack("""
                {"agentId":"unknown","confidence":0.8,"reasoning":"Unavailable agent."}
                """);
    }

    @Test
    void malformedResponseFallsBackToRuleBasedRouting() {
        assertFallsBack("this is not JSON");
    }

    @Test
    void llmExceptionFallsBackToRuleBasedRouting() {
        AiRoutingStrategy strategy = strategy(prompt -> {
            throw new IllegalStateException("provider failed");
        });

        assertFallbackRecommendations(strategy.recommend(order, List.of(otherAgent, lowLoadAgent)));
    }

    @Test
    void confidenceOutsideRangeFallsBackToRuleBasedRouting() {
        assertFallsBack("""
                {"agentId":"A2","confidence":1.2,"reasoning":"Out of range."}
                """);
    }

    @Test
    void missingNullOrWronglyTypedRequiredFieldsFallBackToRuleBasedRouting() {
        List<String> invalidResponses = List.of(
                "{\"confidence\":0.8,\"reasoning\":\"Missing agent.\"}",
                "{\"agentId\":null,\"confidence\":0.8,\"reasoning\":\"Null agent.\"}",
                "{\"agentId\":\" \",\"confidence\":0.8,\"reasoning\":\"Blank agent.\"}",
                "{\"agentId\":\"A2\",\"reasoning\":\"Missing confidence.\"}",
                "{\"agentId\":\"A2\",\"confidence\":null,\"reasoning\":\"Null confidence.\"}",
                "{\"agentId\":\"A2\",\"confidence\":\"0.8\",\"reasoning\":\"String confidence.\"}",
                "{\"agentId\":\"A2\",\"confidence\":-0.1,\"reasoning\":\"Low confidence.\"}",
                "{\"agentId\":\"A2\",\"confidence\":1.1,\"reasoning\":\"High confidence.\"}",
                "{\"agentId\":\"A2\",\"confidence\":0.8}",
                "{\"agentId\":\"A2\",\"confidence\":0.8,\"reasoning\":null}",
                "{\"agentId\":\"A2\",\"confidence\":0.8,\"reasoning\":\" \"}",
                "{\"agentId\":\"A2\",\"confidence\":0.8,\"reasoning\":\"Valid.\"} trailing data",
                "[]");

        for (String response : invalidResponses) {
            assertFallsBack(response);
        }
    }

    @Test
    void confidenceBoundaryValuesRemainValid() {
        for (double confidence : List.of(0.0, 1.0)) {
            AiRoutingStrategy strategy = strategy(prompt -> """
                    {"agentId":"A2","confidence":%s,"reasoning":"Valid boundary."}
                    """.formatted(confidence));

            List<RoutingRecommendation> recommendations =
                    strategy.recommend(order, List.of(otherAgent, lowLoadAgent));

            assertEquals(otherAgent, recommendations.get(0).agent());
            assertEquals(confidence, recommendations.get(0).confidence());
        }
    }

    @Test
    void emptyAvailableAgentListReturnsNoRecommendations() {
        AiRoutingStrategy strategy = strategy(prompt -> {
            throw new AssertionError("LLM must not be called without available agents");
        });

        assertTrue(strategy.recommend(order, List.of()).isEmpty());
    }

    @Test
    void offlineRecommendationUsesOfflineReplanPrompt() {
        AtomicReference<String> submittedPrompt = new AtomicReference<>();
        AiRoutingStrategy strategy = strategy(prompt -> {
            submittedPrompt.set(prompt);
            return """
                    {"agentId":"A2","confidence":0.8,"reasoning":"Suitable replacement."}
                    """;
        });

        strategy.recommend(order, List.of(otherAgent), TriggerReason.AGENT_OFFLINE);

        assertTrue(submittedPrompt.get().contains("AGENT_OFFLINE"));
        assertTrue(submittedPrompt.get().contains("gone OFFLINE"));
        assertTrue(!submittedPrompt.get().contains("new initial assignment"));
    }

    @Test
    void fallbackLogsOrderTriggerAndSanitizedCause() {
        Logger logger = (Logger) LoggerFactory.getLogger(AiRoutingStrategy.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AiRoutingStrategy strategy = strategy(prompt -> "invalid JSON");

            assertFallbackRecommendations(
                    strategy.recommend(order, List.of(otherAgent, lowLoadAgent), TriggerReason.INITIAL));

            assertEquals(Level.WARN, appender.list.get(0).getLevel());
            String logMessage = appender.list.get(0).getFormattedMessage();
            assertTrue(logMessage.contains("O1"));
            assertTrue(logMessage.contains("INITIAL"));
            assertTrue(logMessage.contains("JsonParseException"));
            assertTrue(!logMessage.contains("invalid JSON"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void assertFallsBack(String response) {
        AiRoutingStrategy strategy = strategy(prompt -> response);
        assertFallbackRecommendations(strategy.recommend(order, List.of(otherAgent, lowLoadAgent)));
    }

    private void assertFallbackRecommendations(List<RoutingRecommendation> recommendations) {
        assertEquals(2, recommendations.size());
        assertEquals(lowLoadAgent, recommendations.get(0).agent());
        assertEquals(RecommendationSource.RULE_BASED_FALLBACK, recommendations.get(0).source());
        assertTrue(recommendations.get(0).reasoning().contains("Rule-based fallback was used"));
    }

    private AiRoutingStrategy strategy(LLMGateway llmGateway) {
        return new AiRoutingStrategy(
                llmGateway,
                new PromptBuilder(),
                new RuleBasedRoutingStrategy(),
                new ObjectMapper());
    }

    private static Agent agent(String id, String name, int activeOrderCount) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(name);
        agent.setActiveOrderCount(activeOrderCount);
        return agent;
    }

    private static Order order(String id, String description) {
        Order order = new Order();
        order.setId(id);
        order.setDescription(description);
        return order;
    }
}
