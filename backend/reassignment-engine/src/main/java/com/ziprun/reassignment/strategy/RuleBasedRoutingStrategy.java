package com.ziprun.reassignment.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.TriggerReason;

@Component("ruleBased")
public class RuleBasedRoutingStrategy implements RoutingStrategy {

    @Override
    public List<RoutingRecommendation> recommend(
            Order order,
            List<Agent> availableAgents,
            TriggerReason triggerReason) {
        List<Agent> sortedAgents = availableAgents.stream()
                .sorted(Comparator.comparingInt(Agent::getActiveOrderCount))
                .toList();

        return IntStream.range(0, sortedAgents.size())
                .mapToObj(index -> {
                    Agent agent = sortedAgents.get(index);
                    String reasoning = index == 0
                            ? "Agent " + agent.getName()
                                    + " is recommended because they currently have the lowest active-order load."
                            : "Agent " + agent.getName()
                                    + " is next in the recommendation order with an active-order load of "
                                    + agent.getActiveOrderCount() + ".";
                    double confidence = index == 0 ? 1.0 : 1.0 / (index + 1);
                    return new RoutingRecommendation(agent, confidence, reasoning);
                })
                .toList();
    }
}
