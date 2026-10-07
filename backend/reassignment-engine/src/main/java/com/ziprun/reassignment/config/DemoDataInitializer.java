package com.ziprun.reassignment.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.OrderStatus;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;

@Component
public class DemoDataInitializer implements CommandLineRunner {

    private final AgentRepository agentRepository;
    private final OrderRepository orderRepository;

    public DemoDataInitializer(AgentRepository agentRepository, OrderRepository orderRepository) {
        this.agentRepository = agentRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (agentRepository.existsById("A1") || orderRepository.existsById("O1")) {
            return;
        }

        Agent maya = agent("A1", "Maya Chen", 2, AgentStatus.BUSY);
        Agent noah = agent("A2", "Noah Patel", 2, AgentStatus.BUSY);
        Agent sophia = agent("A3", "Sophia Brooks", 2, AgentStatus.AVAILABLE);
        Agent liam = agent("A4", "Liam Rivera", 1, AgentStatus.AVAILABLE);
        Agent ava = agent("A5", "Ava Thompson", 1, AgentStatus.AVAILABLE);
        agentRepository.saveAll(List.of(maya, noah, sophia, liam, ava));

        orderRepository.saveAll(List.of(
                order("O1", "Grocery delivery to Cedar Street", maya),
                order("O2", "Pharmacy pickup for Elm Avenue", maya),
                order("O3", "Restaurant meal delivery downtown", noah),
                order("O4", "Express document delivery to City Hall", noah),
                order("O5", "Parcel delivery to Westlake Apartments", sophia),
                order("O6", "Bakery order delivery to Pine Road", sophia),
                order("O7", "Pet supplies delivery to Oak Lane", liam),
                order("O8", "Electronics parcel delivery to Hillcrest", ava)));
    }

    private Agent agent(String id, String name, int activeOrderCount, AgentStatus status) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(name);
        agent.setActiveOrderCount(activeOrderCount);
        agent.setStatus(status);
        return agent;
    }

    private Order order(String id, String description, Agent assignedAgent) {
        Order order = new Order();
        order.setId(id);
        order.setDescription(description);
        order.setAssignedAgent(assignedAgent);
        order.setStatus(OrderStatus.ASSIGNED);
        return order;
    }
}
