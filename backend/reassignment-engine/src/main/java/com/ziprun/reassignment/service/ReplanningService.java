package com.ziprun.reassignment.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.OrderStatus;
import com.ziprun.reassignment.entity.TriggerReason;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.strategy.RoutingEngine;
import com.ziprun.reassignment.strategy.RoutingRecommendation;

@Service
public class ReplanningService {

    private static final Logger logger = LoggerFactory.getLogger(ReplanningService.class);

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final SuggestionService suggestionService;
    private final RoutingEngine routingEngine;

    public ReplanningService(
            OrderRepository orderRepository,
            AgentRepository agentRepository,
            SuggestionService suggestionService,
            RoutingEngine routingEngine) {
        this.orderRepository = orderRepository;
        this.agentRepository = agentRepository;
        this.suggestionService = suggestionService;
        this.routingEngine = routingEngine;
    }

    public void replanOrdersForOfflineAgent(String agentId) {
        List<Order> affectedOrders = orderRepository.findByAssignedAgent_IdAndStatus(
                agentId,
                OrderStatus.ASSIGNED);

        for (Order order : affectedOrders) {
            try {
                replanOrder(order);
            } catch (RuntimeException exception) {
                logger.error("Failed to re-plan order {} after agent {} went offline",
                        order.getId(), agentId, exception);
            }
        }
    }

    private void replanOrder(Order order) {
        if (suggestionService.findPendingSuggestion(order.getId(), TriggerReason.AGENT_OFFLINE).isPresent()) {
            logger.info("Skipping order {} because a pending AGENT_OFFLINE suggestion already exists",
                    order.getId());
            return;
        }

        List<Agent> availableAgents = agentRepository.findByStatus(AgentStatus.AVAILABLE);
        if (availableAgents.isEmpty()) {
            logger.warn("Cannot re-plan order {} after agent went offline: no available agents",
                    order.getId());
            return;
        }

        List<RoutingRecommendation> recommendations =
                routingEngine.recommend(order, availableAgents, TriggerReason.AGENT_OFFLINE);
        if (recommendations.isEmpty()) {
            logger.warn("Cannot re-plan order {} after agent went offline: routing returned no recommendations",
                    order.getId());
            return;
        }

        RoutingRecommendation recommendation = recommendations.get(0);
        suggestionService.createSuggestion(
                order,
                recommendation.agent(),
                recommendation.confidence(),
                recommendation.reasoning(),
                TriggerReason.AGENT_OFFLINE,
                recommendation.source());
    }
}
