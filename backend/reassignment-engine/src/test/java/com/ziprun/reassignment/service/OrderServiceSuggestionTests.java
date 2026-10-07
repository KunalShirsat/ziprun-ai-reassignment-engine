package com.ziprun.reassignment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ziprun.reassignment.dto.SuggestionResponse;
import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.OrderStatus;
import com.ziprun.reassignment.entity.SuggestionStatus;
import com.ziprun.reassignment.entity.TriggerReason;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceSuggestionTests {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private SuggestionService suggestionService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, agentRepository, suggestionService);
    }

    @Test
    void initialSuggestionRequestReturnsProcessingResponseWithoutRoutingSynchronously() {
        SuggestionResponse processing = new SuggestionResponse(
                java.util.UUID.randomUUID(),
                "order-1",
                null,
                null,
                0.0,
                "Generating recommendation.",
                SuggestionStatus.PROCESSING,
                TriggerReason.INITIAL);
        when(suggestionService.requestInitialSuggestion("order-1")).thenReturn(processing);

        SuggestionResponse response = orderService.generateSuggestion("order-1");

        assertEquals(TriggerReason.INITIAL, response.triggerReason());
        assertEquals(SuggestionStatus.PROCESSING, response.status());
        assertEquals("order-1", response.orderId());
        assertEquals(0.0, response.confidence());
        assertSame(processing, response);
        verify(suggestionService).requestInitialSuggestion("order-1");
        verify(agentRepository, never()).findByStatus(any());
    }

    @Test
    void requestDelegatesOrderValidationAndProcessingCreationToSuggestionService() {
        when(suggestionService.requestInitialSuggestion("missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: missing"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.generateSuggestion("missing"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private Order order(String id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setDescription("Test order");
        order.setStatus(status);
        return order;
    }

    private Agent agent(String id, String name, int activeOrderCount, AgentStatus status) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(name);
        agent.setActiveOrderCount(activeOrderCount);
        agent.setStatus(status);
        return agent;
    }
}
