package com.ziprun.reassignment.ai;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;

@Component
public class PromptBuilder {

    public String buildInitialPrompt(Order order, List<Agent> availableAgents) {
        return """
                Recommend an agent for an INITIAL assignment.
                This is a new initial assignment/recommendation, not an offline-agent re-planning task.
                Select the best agent from the available agents using current active-order loads.
                Order:
                %s
                Available agents:
                %s
                Respond with only a machine-readable JSON object containing exactly these fields:
                {"agentId":"...", "confidence":0.0, "reasoning":"..."}
                confidence must be between 0.0 and 1.0. agentId must be one of the available agent IDs.
                """.formatted(formatOrder(order), formatAgents(availableAgents));
    }

    public String buildOfflineReplanPrompt(Order order, List<Agent> availableAgents) {
        return """
                Re-plan an assignment because the currently assigned agent has gone OFFLINE.
                Trigger context: AGENT_OFFLINE re-planning; choose a replacement agent for the affected order.
                Order requiring re-planning:
                %s
                Available replacement agents:
                %s
                Respond with only a machine-readable JSON object containing exactly these fields:
                {"agentId":"...", "confidence":0.0, "reasoning":"..."}
                confidence must be between 0.0 and 1.0. agentId must be one of the available agent IDs.
                """.formatted(formatOrder(order), formatAgents(availableAgents));
    }

    private String formatOrder(Order order) {
        return "orderId: " + order.getId()
                + "\norderDescription: " + order.getDescription();
    }

    private String formatAgents(List<Agent> availableAgents) {
        if (availableAgents.isEmpty()) {
            return "(none)";
        }
        return availableAgents.stream()
                .map(agent -> "agentId: " + agent.getId()
                        + ", agentName: " + agent.getName()
                        + ", activeOrderCount: " + agent.getActiveOrderCount())
                .collect(Collectors.joining("\n"));
    }
}
