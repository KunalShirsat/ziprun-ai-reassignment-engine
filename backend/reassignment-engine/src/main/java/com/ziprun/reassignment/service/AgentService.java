package com.ziprun.reassignment.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ziprun.reassignment.dto.AgentResponse;
import com.ziprun.reassignment.dto.UpdateAgentStatusRequest;
import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;
import com.ziprun.reassignment.event.AgentOfflineEvent;
import com.ziprun.reassignment.repository.AgentRepository;

@Service
public class AgentService {

    private final AgentRepository agentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentService(AgentRepository agentRepository, ApplicationEventPublisher eventPublisher) {
        this.agentRepository = agentRepository;
        this.eventPublisher = eventPublisher;
    }

    public AgentResponse updateStatus(String agentId, UpdateAgentStatusRequest request) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        boolean newlyOffline = agent.getStatus() != AgentStatus.OFFLINE
                && request.status() == AgentStatus.OFFLINE;
        agent.setStatus(request.status());
        Agent savedAgent = agentRepository.save(agent);
        if (newlyOffline) {
            eventPublisher.publishEvent(new AgentOfflineEvent(agentId));
        }
        return toResponse(savedAgent);
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> getAgents() {
        return agentRepository.findAll().stream().map(this::toResponse).toList();
    }

    private AgentResponse toResponse(Agent agent) {
        return new AgentResponse(
                agent.getId(),
                agent.getName(),
                agent.getActiveOrderCount(),
                agent.getStatus());
    }
}
