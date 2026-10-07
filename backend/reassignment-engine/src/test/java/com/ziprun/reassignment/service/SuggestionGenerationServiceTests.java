package com.ziprun.reassignment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ziprun.reassignment.ai.LLMGateway;
import com.ziprun.reassignment.ai.PromptBuilder;
import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.OrderStatus;
import com.ziprun.reassignment.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.entity.SuggestionStatus;
import com.ziprun.reassignment.entity.TriggerReason;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.SuggestionRepository;
import com.ziprun.reassignment.strategy.AiRoutingStrategy;
import com.ziprun.reassignment.strategy.RoutingEngine;
import com.ziprun.reassignment.strategy.RoutingRecommendation;
import com.ziprun.reassignment.strategy.RuleBasedRoutingStrategy;

@ExtendWith(MockitoExtension.class)
class SuggestionGenerationServiceTests {

    @Mock
    private SuggestionRepository suggestionRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private RoutingEngine routingEngine;

    private SuggestionGenerationService generationService;
    private ReassignmentSuggestion suggestion;
    private Agent recommendedAgent;

    @BeforeEach
    void setUp() {
        generationService = new SuggestionGenerationService(
                suggestionRepository,
                agentRepository,
                routingEngine);
        Order order = new Order();
        order.setId("O1");
        order.setStatus(OrderStatus.ASSIGNED);
        suggestion = new ReassignmentSuggestion();
        suggestion.setId(UUID.randomUUID());
        suggestion.setOrder(order);
        suggestion.setStatus(SuggestionStatus.PROCESSING);
        suggestion.setTriggerReason(TriggerReason.INITIAL);
        suggestion.setConfidence(0.0);
        suggestion.setReasoning("Generating recommendation.");
        recommendedAgent = new Agent();
        recommendedAgent.setId("A4");
        recommendedAgent.setName("Agent A4");
        recommendedAgent.setStatus(AgentStatus.AVAILABLE);
    }

    @Test
    void completesProcessingSuggestionInPlaceWithRoutingResult() {
        when(suggestionRepository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));
        when(agentRepository.findByStatus(AgentStatus.AVAILABLE)).thenReturn(List.of(recommendedAgent));
        when(routingEngine.recommend(
                suggestion.getOrder(), List.of(recommendedAgent), TriggerReason.INITIAL))
                .thenReturn(List.of(new RoutingRecommendation(recommendedAgent, 0.82, "AI-selected agent.")));

        generationService.generate(suggestion.getId());

        assertEquals(SuggestionStatus.PENDING, suggestion.getStatus());
        assertEquals("A4", suggestion.getRecommendedAgent().getId());
        assertEquals(0.82, suggestion.getConfidence());
        assertEquals("AI-selected agent.", suggestion.getReasoning());
        verify(suggestionRepository).save(suggestion);
        assertSame(suggestion, suggestionRepository.findById(suggestion.getId()).orElseThrow());
    }

    @Test
    void llmFailureUsesRuleBasedFallbackAndCompletesSameSuggestionAsPending() {
        when(suggestionRepository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));
        when(agentRepository.findByStatus(AgentStatus.AVAILABLE)).thenReturn(List.of(recommendedAgent));
        LLMGateway failingGateway = prompt -> {
            throw new IllegalStateException("mock provider failure");
        };
        AiRoutingStrategy aiStrategy = new AiRoutingStrategy(
                failingGateway,
                new PromptBuilder(),
                new RuleBasedRoutingStrategy(),
                new ObjectMapper());
        RoutingEngine aiRoutingEngine = new RoutingEngine(
                java.util.Map.of("ai", aiStrategy),
                "ai");
        SuggestionGenerationService aiGenerationService = new SuggestionGenerationService(
                suggestionRepository,
                agentRepository,
                aiRoutingEngine);

        aiGenerationService.generate(suggestion.getId());

        assertEquals(SuggestionStatus.PENDING, suggestion.getStatus());
        assertEquals("A4", suggestion.getRecommendedAgent().getId());
        org.junit.jupiter.api.Assertions.assertTrue(
                suggestion.getReasoning().contains("Rule-based fallback was used"));
        verify(suggestionRepository).save(suggestion);
    }
}
