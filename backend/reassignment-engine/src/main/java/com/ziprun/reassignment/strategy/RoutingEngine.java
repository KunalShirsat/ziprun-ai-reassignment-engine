package com.ziprun.reassignment.strategy;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.TriggerReason;

@Component
public class RoutingEngine {

    private final Map<String, RoutingStrategy> strategies;
    private volatile String activeStrategyName;

    public RoutingEngine(
            Map<String, RoutingStrategy> strategies,
            @Value("${routing.strategy}") String strategyName) {
        if (!strategies.containsKey(strategyName)) {
            throw new IllegalStateException("No routing strategy configured with name: " + strategyName);
        }
        this.strategies = Map.copyOf(strategies);
        this.activeStrategyName = strategyName;
    }

    public List<RoutingRecommendation> recommend(Order order, List<Agent> availableAgents) {
        return recommend(order, availableAgents, TriggerReason.INITIAL);
    }

    public List<RoutingRecommendation> recommend(
            Order order,
            List<Agent> availableAgents,
            TriggerReason triggerReason) {
        return strategies.get(activeStrategyName).recommend(order, availableAgents, triggerReason);
    }

    public String getActiveStrategyName() {
        return activeStrategyName;
    }

    public String switchStrategy(String strategyName) {
        if (!strategies.containsKey(strategyName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown routing strategy: " + strategyName);
        }
        activeStrategyName = strategyName;
        return activeStrategyName;
    }
}
