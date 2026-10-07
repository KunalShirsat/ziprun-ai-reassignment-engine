package com.ziprun.reassignment.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.TriggerReason;

class RoutingEngineTests {

    @Test
    void selectsTheConfiguredRuleBasedStrategy() {
        RoutingEngine routingEngine = new RoutingEngine(
                Map.of("ruleBased", new RuleBasedRoutingStrategy()),
                "ruleBased");
        Agent availableAgent = agent("agent-1", 0);

        assertEquals(availableAgent,
                routingEngine.recommend(new Order(), List.of(availableAgent)).get(0).agent());
    }

    @Test
    void failsClearlyWhenConfiguredStrategyDoesNotExist() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new RoutingEngine(Map.of(), "missingStrategy"));

        assertEquals("No routing strategy configured with name: missingStrategy", exception.getMessage());
    }

    @Test
    void canSwitchStrategiesAtRuntimeAndSubsequentRoutingUsesCurrentStrategy() {
        Agent agent = agent("agent-1", 0);
        RoutingStrategy aiStrategy = (order, agents, triggerReason) ->
                List.of(new RoutingRecommendation(agent, 0.7, "AI recommendation"));
        RoutingEngine routingEngine = new RoutingEngine(
                Map.of(
                        "ruleBased", new RuleBasedRoutingStrategy(),
                        "ai", aiStrategy),
                "ruleBased");

        assertEquals("ruleBased", routingEngine.getActiveStrategyName());
        assertEquals(1.0, routingEngine.recommend(new Order(), List.of(agent)).get(0).confidence());

        assertEquals("ai", routingEngine.switchStrategy("ai"));
        assertEquals("AI recommendation",
                routingEngine.recommend(new Order(), List.of(agent)).get(0).reasoning());

        assertEquals("ruleBased", routingEngine.switchStrategy("ruleBased"));
        assertEquals(1.0, routingEngine.recommend(new Order(), List.of(agent)).get(0).confidence());
    }

    @Test
    void runtimeSwitchRejectsUnknownStrategy() {
        RoutingEngine routingEngine = new RoutingEngine(
                Map.of("ruleBased", new RuleBasedRoutingStrategy()),
                "ruleBased");

        org.springframework.web.server.ResponseStatusException exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> routingEngine.switchStrategy("missingStrategy"));

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("ruleBased", routingEngine.getActiveStrategyName());
    }

    @Test
    void forwardsTriggerContextToCurrentStrategy() {
        Agent agent = agent("agent-1", 0);
        RoutingStrategy strategy = (order, agents, triggerReason) -> {
            assertEquals(TriggerReason.AGENT_OFFLINE, triggerReason);
            return List.of(new RoutingRecommendation(agent, 0.7, "offline"));
        };
        RoutingEngine routingEngine = new RoutingEngine(Map.of("ai", strategy), "ai");

        assertEquals("offline",
                routingEngine.recommend(new Order(), List.of(agent), TriggerReason.AGENT_OFFLINE)
                        .get(0).reasoning());
    }

    private Agent agent(String id, int activeOrderCount) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(id);
        agent.setActiveOrderCount(activeOrderCount);
        return agent;
    }
}
