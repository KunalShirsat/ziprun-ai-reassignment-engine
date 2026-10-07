package com.ziprun.reassignment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ziprun.reassignment.dto.CreateOrderRequest;
import com.ziprun.reassignment.dto.OrderResponse;
import com.ziprun.reassignment.dto.SuggestionResponse;
import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.OrderStatus;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final SuggestionService suggestionService;

    public OrderService(
            OrderRepository orderRepository,
            AgentRepository agentRepository,
            SuggestionService suggestionService) {
        this.orderRepository = orderRepository;
        this.agentRepository = agentRepository;
        this.suggestionService = suggestionService;
    }

    public SuggestionResponse generateSuggestion(String orderId) {
        return suggestionService.requestInitialSuggestion(orderId);
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Agent assignedAgent = agentRepository.findById(request.assignedAgentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Agent not found: " + request.assignedAgentId()));

        Order order = new Order();
        order.setId(request.id());
        order.setDescription(request.description());
        order.setAssignedAgent(assignedAgent);
        order.setStatus(OrderStatus.ASSIGNED);

        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(OrderStatus status) {
        List<Order> orders = status == null
                ? orderRepository.findAll()
                : orderRepository.findByStatus(status);
        return orders.stream().map(this::toResponse).toList();
    }

    private OrderResponse toResponse(Order order) {
        Agent assignedAgent = order.getAssignedAgent();
        return new OrderResponse(
                order.getId(),
                order.getDescription(),
                assignedAgent == null ? null : assignedAgent.getId(),
                assignedAgent == null ? null : assignedAgent.getName(),
                order.getStatus());
    }
}
