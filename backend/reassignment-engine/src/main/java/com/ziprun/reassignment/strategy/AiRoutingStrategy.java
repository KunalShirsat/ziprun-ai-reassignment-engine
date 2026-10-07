package com.ziprun.reassignment.strategy;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ziprun.reassignment.ai.AIResponse;
import com.ziprun.reassignment.ai.LLMGateway;
import com.ziprun.reassignment.ai.PromptBuilder;
import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.TriggerReason;

@Component("ai")
public class AiRoutingStrategy implements RoutingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(AiRoutingStrategy.class);

    private final LLMGateway llmGateway;
    private final PromptBuilder promptBuilder;
    private final RuleBasedRoutingStrategy ruleBasedRoutingStrategy;
    private final ObjectMapper objectMapper;

    public AiRoutingStrategy(
            LLMGateway llmGateway,
            PromptBuilder promptBuilder,
            RuleBasedRoutingStrategy ruleBasedRoutingStrategy,
            ObjectMapper objectMapper) {
        this.llmGateway = llmGateway;
        this.promptBuilder = promptBuilder;
        this.ruleBasedRoutingStrategy = ruleBasedRoutingStrategy;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RoutingRecommendation> recommend(
            Order order,
            List<Agent> availableAgents,
            TriggerReason triggerReason) {
        String prompt = triggerReason == TriggerReason.AGENT_OFFLINE
                ? promptBuilder.buildOfflineReplanPrompt(order, availableAgents)
                : promptBuilder.buildInitialPrompt(order, availableAgents);
        return recommend(prompt, order, availableAgents, triggerReason);
    }

    private List<RoutingRecommendation> recommend(
            String prompt,
            Order order,
            List<Agent> availableAgents,
            TriggerReason triggerReason) {
        if (availableAgents.isEmpty()) {
            return List.of();
        }

        try {
            String response = llmGateway.generate(prompt);
            if (response == null || response.isBlank()) {
                return fallback(order, availableAgents, triggerReason, "empty response");
            }

            AIResponse aiResponse = objectMapper.readValue(response, AIResponse.class);
            Agent recommendedAgent = validateAndFindAgent(aiResponse, availableAgents);
            return List.of(new RoutingRecommendation(
                    recommendedAgent,
                    aiResponse.confidence(),
                    aiResponse.reasoning()));
        } catch (JsonProcessingException | RuntimeException exception) {
            return fallback(order, availableAgents, triggerReason, exception.getClass().getSimpleName());
        }
    }

    private Agent validateAndFindAgent(AIResponse response, List<Agent> availableAgents) {
        if (response == null
                || response.agentId() == null
                || !Double.isFinite(response.confidence())
                || response.confidence() < 0.0
                || response.confidence() > 1.0
                || response.reasoning() == null
                || response.reasoning().isBlank()) {
            throw new IllegalArgumentException("LLM response failed validation");
        }

        return availableAgents.stream()
                .filter(agent -> Objects.equals(agent.getId(), response.agentId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("LLM response selected an unavailable agent"));
    }

    private List<RoutingRecommendation> fallback(
            Order order,
            List<Agent> availableAgents,
            TriggerReason triggerReason,
            String cause) {
        logger.warn(
                "AI routing failed for order {} during {}. Falling back to rule-based routing. Cause: {}",
                order.getId(),
                triggerReason,
                cause);
        return ruleBasedRoutingStrategy.recommend(order, availableAgents).stream()
                .map(recommendation -> new RoutingRecommendation(
                        recommendation.agent(),
                        recommendation.confidence(),
                        "Rule-based fallback was used because the AI recommendation was unavailable or invalid. "
                                + recommendation.reasoning()))
                .toList();
    }
}
