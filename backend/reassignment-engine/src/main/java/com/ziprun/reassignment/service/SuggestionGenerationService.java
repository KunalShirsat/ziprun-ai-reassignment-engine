package com.ziprun.reassignment.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;
import com.ziprun.reassignment.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.entity.SuggestionStatus;
import com.ziprun.reassignment.entity.TriggerReason;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.SuggestionRepository;
import com.ziprun.reassignment.strategy.RoutingEngine;
import com.ziprun.reassignment.strategy.RoutingRecommendation;

@Service
public class SuggestionGenerationService {

    private final SuggestionRepository suggestionRepository;
    private final AgentRepository agentRepository;
    private final RoutingEngine routingEngine;

    public SuggestionGenerationService(
            SuggestionRepository suggestionRepository,
            AgentRepository agentRepository,
            RoutingEngine routingEngine) {
        this.suggestionRepository = suggestionRepository;
        this.agentRepository = agentRepository;
        this.routingEngine = routingEngine;
    }

    @Transactional
    public void generate(UUID suggestionId) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Processing suggestion not found: " + suggestionId));
        if (suggestion.getStatus() != SuggestionStatus.PROCESSING) {
            return;
        }

        List<Agent> availableAgents = agentRepository.findByStatus(AgentStatus.AVAILABLE);
        List<RoutingRecommendation> recommendations = routingEngine.recommend(
                suggestion.getOrder(),
                availableAgents,
                TriggerReason.INITIAL);
        if (recommendations.isEmpty()) {
            throw new IllegalStateException("Routing returned no recommendations");
        }

        RoutingRecommendation recommendation = recommendations.get(0);
        suggestion.setRecommendedAgent(recommendation.agent());
        suggestion.setConfidence(recommendation.confidence());
        suggestion.setReasoning(recommendation.reasoning());
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestionRepository.save(suggestion);
    }
}
