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
                Make an INITIAL routing recommendation for a delivery order. This is a new assignment decision,
                not a recovery or AGENT_OFFLINE replanning task.
                Use only the supplied order details and candidate information. Compare current active-order load,
                availability, and suitability supported by the order description. Do not assume or invent facts
                such as location, skills, distance, history, or service-level commitments.
                Select exactly one agentId from the supplied available candidates; never recommend an agent
                who is not listed as a candidate.
                Order context:
                %s
                Available candidate agents:
                %s
                Return only a JSON object with exactly these fields and types:
                {"agentId":"...", "confidence":0.0, "reasoning":"..."}
                confidence must be a number from 0.0 to 1.0. reasoning must be concise (no more than two short
                sentences), evidence-based, and useful to a dispatcher. Explain why the selected candidate fits
                this order using the supplied workload and context. Do not invent facts or use generic claims
                such as "this agent is the best choice".
                """.formatted(
                formatOrder(order),
                formatAgents(availableAgents));
    }

    public String buildOfflineReplanPrompt(Order order, List<Agent> availableAgents) {
        return """
                Perform an AGENT_OFFLINE recovery replanning decision. The order is being reconsidered because
                its incumbent assigned agent has gone OFFLINE and is now unavailable. For this recovery decision,
                treat the order as stranded and requiring a replacement; the stored assignment identifies the unavailable incumbent,
                not a usable candidate.
                Order requiring recovery:
                %s
                Incumbent agent:
                %s
                Available replacement agents:
                %s
                Choose exactly one replacement agentId from the supplied available candidates and never select
                the incumbent unless that agent is explicitly listed among the candidates.
                Base the decision only on supplied order details, candidate availability, current active-order
                loads, and suitability supported by the order description. Do not assume or invent location,
                skills, distance, history, or service-level commitments.
                Return only a JSON object with exactly these fields and types:
                {"agentId":"...", "confidence":0.0, "reasoning":"..."}
                confidence must be a number from 0.0 to 1.0. reasoning must be concise (no more than two short
                sentences), evidence-based, and useful to a dispatcher. Explain why the selected replacement fits
                this order using the supplied workload and recovery context. Do not invent facts or use generic
                claims such as "this agent is the best choice".
                """.formatted(
                formatOrder(order),
                formatIncumbent(order),
                formatAgents(availableAgents));
    }

    private String formatIncumbent(Order order) {
        Agent incumbent = order.getAssignedAgent();
        if (incumbent == null) {
            return "Incumbent identity is not available.";
        }
        return "agentId: " + incumbent.getId()
                + "\nagentName: " + incumbent.getName()
                + "\nstatus: " + incumbent.getStatus()
                + "\nactiveOrderCount: " + incumbent.getActiveOrderCount();
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
                        + ", status: " + agent.getStatus()
                        + ", activeOrderCount: " + agent.getActiveOrderCount())
                .collect(Collectors.joining("\n"));
    }
}
