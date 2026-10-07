package com.ziprun.reassignment.ai;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;
import com.ziprun.reassignment.entity.Order;

class PromptBuilderTests {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void initialPromptContainsOrderAndAgentDetailsAndInitialContext() {
        String prompt = normalize(promptBuilder.buildInitialPrompt(order(), List.of(agent())));

        assertTrue(prompt.contains("order-1"));
        assertTrue(prompt.contains("Collect parcel"));
        assertTrue(prompt.contains("agent-1"));
        assertTrue(prompt.contains("Avery"));
        assertTrue(prompt.contains("status: AVAILABLE"));
        assertTrue(prompt.contains("activeOrderCount: 2"));
        assertTrue(prompt.contains("INITIAL"));
        assertTrue(prompt.contains("Select exactly one agentId"));
        assertTrue(prompt.contains("exactly these fields and types"));
        assertTrue(prompt.contains("evidence-based"));
        assertTrue(prompt.contains("dispatcher"));
        assertTrue(prompt.contains("Do not assume or invent"));
        assertTrue(prompt.contains("generic claims"));
    }

    @Test
    void offlinePromptIncludesIncumbentAndRecoveryContextAndReplacementCandidates() {
        Order order = order();
        Agent incumbent = new Agent();
        incumbent.setId("offline-agent-9");
        incumbent.setName("Morgan");
        incumbent.setStatus(AgentStatus.OFFLINE);
        incumbent.setActiveOrderCount(3);
        order.setAssignedAgent(incumbent);

        String prompt = normalize(promptBuilder.buildOfflineReplanPrompt(order, List.of(agent())));

        assertTrue(prompt.contains("AGENT_OFFLINE"));
        assertTrue(prompt.contains("replanning decision"));
        assertTrue(prompt.contains("incumbent assigned agent has gone OFFLINE"));
        assertTrue(prompt.contains("stranded"));
        assertTrue(prompt.contains("offline-agent-9"));
        assertTrue(prompt.contains("Morgan"));
        assertTrue(prompt.contains("status: OFFLINE"));
        assertTrue(prompt.contains("activeOrderCount: 3"));
        assertTrue(prompt.contains("agent-1"));
        assertTrue(prompt.contains("Avery"));
        assertTrue(prompt.contains("status: AVAILABLE"));
        assertTrue(prompt.contains("activeOrderCount: 2"));
        assertTrue(prompt.contains("Choose exactly one replacement agentId"));
        assertTrue(prompt.contains("exactly these fields and types"));
        assertTrue(prompt.contains("evidence-based"));
        assertTrue(prompt.contains("dispatcher"));
        assertTrue(prompt.contains("Do not assume or invent"));
        assertTrue(prompt.contains("generic claims"));
    }

    @Test
    void offlinePromptStatesWhenIncumbentIdentityIsUnavailable() {
        String prompt = normalize(promptBuilder.buildOfflineReplanPrompt(order(), List.of(agent())));

        assertTrue(prompt.contains("Incumbent identity is not available."));
    }

    @Test
    void initialAndOfflinePromptsHaveMateriallyDifferentDecisionContext() {
        String initialPrompt = normalize(promptBuilder.buildInitialPrompt(order(), List.of(agent())));
        String offlinePrompt = normalize(promptBuilder.buildOfflineReplanPrompt(order(), List.of(agent())));

        assertNotEquals(initialPrompt, offlinePrompt);
        assertTrue(initialPrompt.contains("new assignment decision"));
        assertTrue(initialPrompt.contains("not a recovery"));
        assertTrue(offlinePrompt.contains("recovery replanning decision"));
        assertTrue(offlinePrompt.contains("stranded"));
    }

    private Order order() {
        Order order = new Order();
        order.setId("order-1");
        order.setDescription("Collect parcel");
        return order;
    }

    private Agent agent() {
        Agent agent = new Agent();
        agent.setId("agent-1");
        agent.setName("Avery");
        agent.setStatus(AgentStatus.AVAILABLE);
        agent.setActiveOrderCount(2);
        return agent;
    }

    private String normalize(String prompt) {
        return prompt.replaceAll("\\s+", " ");
    }
}
