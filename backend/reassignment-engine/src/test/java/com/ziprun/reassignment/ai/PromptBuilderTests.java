package com.ziprun.reassignment.ai;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;

class PromptBuilderTests {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void initialPromptContainsOrderAndAgentDetailsAndInitialContext() {
        String prompt = promptBuilder.buildInitialPrompt(order(), List.of(agent()));

        assertTrue(prompt.contains("order-1"));
        assertTrue(prompt.contains("Collect parcel"));
        assertTrue(prompt.contains("agent-1"));
        assertTrue(prompt.contains("Avery"));
        assertTrue(prompt.contains("activeOrderCount: 2"));
        assertTrue(prompt.contains("INITIAL assignment"));
        assertTrue(prompt.contains("agentId"));
        assertTrue(prompt.contains("confidence"));
        assertTrue(prompt.contains("reasoning"));
    }

    @Test
    void initialAndOfflinePromptsUseDifferentTriggerContexts() {
        String initialPrompt = promptBuilder.buildInitialPrompt(order(), List.of(agent()));
        String offlinePrompt = promptBuilder.buildOfflineReplanPrompt(order(), List.of(agent()));

        assertNotEquals(initialPrompt, offlinePrompt);
        assertTrue(initialPrompt.contains("INITIAL"));
        assertTrue(offlinePrompt.contains("AGENT_OFFLINE"));
        assertTrue(offlinePrompt.contains("gone OFFLINE"));
        assertTrue(offlinePrompt.contains("agent-1"));
        assertTrue(offlinePrompt.contains("Avery"));
        assertTrue(offlinePrompt.contains("activeOrderCount: 2"));
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
        agent.setActiveOrderCount(2);
        return agent;
    }
}
