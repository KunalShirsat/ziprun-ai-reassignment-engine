package com.ziprun.reassignment.strategy;

import java.util.List;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.TriggerReason;

public interface RoutingStrategy {

    List<RoutingRecommendation> recommend(
            Order order,
            List<Agent> availableAgents,
            TriggerReason triggerReason);

    default List<RoutingRecommendation> recommend(Order order, List<Agent> availableAgents) {
        return recommend(order, availableAgents, TriggerReason.INITIAL);
    }
}
