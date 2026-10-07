package com.ziprun.reassignment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ziprun.reassignment.dto.SuggestionResponse;
import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.OrderStatus;
import com.ziprun.reassignment.entity.RecommendationSource;
import com.ziprun.reassignment.entity.SuggestionStatus;
import com.ziprun.reassignment.entity.TriggerReason;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.strategy.RoutingEngine;
import com.ziprun.reassignment.strategy.RoutingRecommendation;

@ExtendWith(MockitoExtension.class)
class ReplanningServiceTests {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private SuggestionService suggestionService;

    @Mock
    private RoutingEngine routingEngine;

    private ReplanningService replanningService;
    private Order affectedOrder;
    private Agent recommendedAgent;

    @BeforeEach
    void setUp() {
        replanningService = new ReplanningService(
                orderRepository,
                agentRepository,
                suggestionService,
                routingEngine);
        affectedOrder = order("order-1", "offline-agent");
        recommendedAgent = agent("available-agent", AgentStatus.AVAILABLE);
    }

    @Test
    void findsAssignedOrdersAndCreatesPendingOfflineSuggestionWithoutReassigning() {
        when(orderRepository.findByAssignedAgent_IdAndStatus("offline-agent", OrderStatus.ASSIGNED))
                .thenReturn(List.of(affectedOrder));
        when(suggestionService.findPendingSuggestion("order-1", TriggerReason.AGENT_OFFLINE))
                .thenReturn(Optional.empty());
        when(agentRepository.findByStatus(AgentStatus.AVAILABLE)).thenReturn(List.of(recommendedAgent));
        when(routingEngine.recommend(affectedOrder, List.of(recommendedAgent), TriggerReason.AGENT_OFFLINE))
                .thenReturn(List.of(new RoutingRecommendation(
                        recommendedAgent, 0.9, "best available", RecommendationSource.AI)));

        replanningService.replanOrdersForOfflineAgent("offline-agent");

        verify(orderRepository).findByAssignedAgent_IdAndStatus("offline-agent", OrderStatus.ASSIGNED);
        verify(suggestionService).createSuggestion(
                affectedOrder,
                recommendedAgent,
                0.9,
                "best available",
                TriggerReason.AGENT_OFFLINE,
                RecommendationSource.AI);
        assertEquals("offline-agent", affectedOrder.getAssignedAgent().getId());
        assertEquals(OrderStatus.ASSIGNED, affectedOrder.getStatus());
    }

    @Test
    void existingPendingOfflineSuggestionPreventsDuplicate() {
        when(orderRepository.findByAssignedAgent_IdAndStatus("offline-agent", OrderStatus.ASSIGNED))
                .thenReturn(List.of(affectedOrder));
        when(suggestionService.findPendingSuggestion("order-1", TriggerReason.AGENT_OFFLINE))
                .thenReturn(Optional.of(new SuggestionResponse(
                        null, "order-1", "existing", "Existing", 0.7, "pending",
                        SuggestionStatus.PENDING, TriggerReason.AGENT_OFFLINE)));

        replanningService.replanOrdersForOfflineAgent("offline-agent");

        verify(agentRepository, never()).findByStatus(any());
        verify(suggestionService, never()).createSuggestion(
                any(), any(), any(double.class), any(), any(), any());
    }

    @Test
    void noAvailableAgentsDoesNotCreateSuggestion() {
        when(orderRepository.findByAssignedAgent_IdAndStatus("offline-agent", OrderStatus.ASSIGNED))
                .thenReturn(List.of(affectedOrder));
        when(suggestionService.findPendingSuggestion("order-1", TriggerReason.AGENT_OFFLINE))
                .thenReturn(Optional.empty());
        when(agentRepository.findByStatus(AgentStatus.AVAILABLE)).thenReturn(List.of());

        replanningService.replanOrdersForOfflineAgent("offline-agent");

        verify(suggestionService, never()).createSuggestion(
                any(), any(), any(double.class), any(), any(), any());
    }

    @Test
    void failureProcessingOneOrderDoesNotPreventNextOrder() {
        Order secondOrder = order("order-2", "offline-agent");
        when(orderRepository.findByAssignedAgent_IdAndStatus("offline-agent", OrderStatus.ASSIGNED))
                .thenReturn(List.of(affectedOrder, secondOrder));
        when(suggestionService.findPendingSuggestion("order-1", TriggerReason.AGENT_OFFLINE))
                .thenReturn(Optional.empty());
        when(agentRepository.findByStatus(AgentStatus.AVAILABLE))
                .thenThrow(new IllegalStateException("temporary repository failure"))
                .thenReturn(List.of(recommendedAgent));
        when(suggestionService.findPendingSuggestion("order-2", TriggerReason.AGENT_OFFLINE))
                .thenReturn(Optional.empty());
        when(routingEngine.recommend(secondOrder, List.of(recommendedAgent), TriggerReason.AGENT_OFFLINE))
                .thenReturn(List.of(new RoutingRecommendation(
                        recommendedAgent, 0.8, "next order", RecommendationSource.RULE_BASED)));

        replanningService.replanOrdersForOfflineAgent("offline-agent");

        verify(suggestionService, never()).createSuggestion(
                eq(affectedOrder), any(), any(double.class), any(), any(), any());
        verify(suggestionService).createSuggestion(
                secondOrder,
                recommendedAgent,
                0.8,
                "next order",
                TriggerReason.AGENT_OFFLINE,
                RecommendationSource.RULE_BASED);
    }

    @Test
    void offlineReplanningUsesRoutingEngineWithOfflineTrigger() {
        when(orderRepository.findByAssignedAgent_IdAndStatus("offline-agent", OrderStatus.ASSIGNED))
                .thenReturn(List.of(affectedOrder));
        when(suggestionService.findPendingSuggestion("order-1", TriggerReason.AGENT_OFFLINE))
                .thenReturn(Optional.empty());
        when(agentRepository.findByStatus(AgentStatus.AVAILABLE)).thenReturn(List.of(recommendedAgent));
        when(routingEngine.recommend(
                affectedOrder, List.of(recommendedAgent), TriggerReason.AGENT_OFFLINE))
                .thenReturn(List.of(new RoutingRecommendation(
                        recommendedAgent, 0.85, "offline re-plan", RecommendationSource.RULE_BASED)));

        replanningService.replanOrdersForOfflineAgent("offline-agent");

        verify(routingEngine).recommend(
                affectedOrder, List.of(recommendedAgent), TriggerReason.AGENT_OFFLINE);
        verify(routingEngine, never()).recommend(any(), any());
        verify(suggestionService).createSuggestion(
                affectedOrder,
                recommendedAgent,
                0.85,
                "offline re-plan",
                TriggerReason.AGENT_OFFLINE,
                RecommendationSource.RULE_BASED);
    }

    private Order order(String id, String assignedAgentId) {
        Order order = new Order();
        order.setId(id);
        order.setDescription("Test order");
        order.setAssignedAgent(agent(assignedAgentId, AgentStatus.OFFLINE));
        order.setStatus(OrderStatus.ASSIGNED);
        return order;
    }

    private Agent agent(String id, AgentStatus status) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(id);
        agent.setStatus(status);
        return agent;
    }
}
