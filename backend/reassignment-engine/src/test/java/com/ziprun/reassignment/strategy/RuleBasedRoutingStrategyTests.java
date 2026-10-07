package com.ziprun.reassignment.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;

class RuleBasedRoutingStrategyTests {

    private final RuleBasedRoutingStrategy strategy = new RuleBasedRoutingStrategy();

    @Test
    void returnsNoRecommendationsWhenNoAgentsAreAvailable() {
        assertTrue(strategy.recommend(new Order(), List.of()).isEmpty());
    }

    @Test
    void sortsAgentsByActiveOrderCountAndRecommendsLowestLoadFirst() {
        Agent busyAgent = agent("busy", 4);
        Agent lowLoadAgent = agent("low", 1);
        Agent mediumLoadAgent = agent("medium", 2);

        List<RoutingRecommendation> recommendations = strategy.recommend(
                new Order(),
                List.of(busyAgent, lowLoadAgent, mediumLoadAgent));

        assertEquals(List.of(lowLoadAgent, mediumLoadAgent, busyAgent),
                recommendations.stream().map(RoutingRecommendation::agent).toList());
        assertEquals(1.0, recommendations.get(0).confidence());
        assertTrue(recommendations.get(0).reasoning().contains("lowest active-order load"));
    }

    private Agent agent(String id, int activeOrderCount) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(id);
        agent.setActiveOrderCount(activeOrderCount);
        return agent;
    }
}
