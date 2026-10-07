package com.ziprun.reassignment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ziprun.reassignment.dto.AgentResponse;
import com.ziprun.reassignment.dto.UpdateAgentStatusRequest;
import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;
import com.ziprun.reassignment.event.AgentOfflineEvent;
import com.ziprun.reassignment.repository.AgentRepository;

@ExtendWith(MockitoExtension.class)
class AgentServiceOfflineEventTests {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AgentService agentService;

    @Test
    void changingAgentToOfflineSavesThenPublishesEvent() {
        Agent agent = agent();
        when(agentRepository.findById("agent-1")).thenReturn(java.util.Optional.of(agent));
        when(agentRepository.save(agent)).thenReturn(agent);

        AgentResponse response = agentService.updateStatus(
                "agent-1",
                new UpdateAgentStatusRequest(AgentStatus.OFFLINE));

        assertEquals(AgentStatus.OFFLINE, response.status());
        InOrder order = inOrder(agentRepository, eventPublisher);
        order.verify(agentRepository).save(agent);
        order.verify(eventPublisher).publishEvent(new AgentOfflineEvent("agent-1"));
    }

    @Test
    void changingAgentToAvailableOrBusyDoesNotPublishEvent() {
        Agent agent = agent();
        when(agentRepository.findById("agent-1")).thenReturn(java.util.Optional.of(agent));
        when(agentRepository.save(agent)).thenReturn(agent);

        agentService.updateStatus("agent-1", new UpdateAgentStatusRequest(AgentStatus.AVAILABLE));
        agentService.updateStatus("agent-1", new UpdateAgentStatusRequest(AgentStatus.BUSY));

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void alreadyOfflineAgentDoesNotPublishAnotherTransitionEvent() {
        Agent agent = agent();
        agent.setStatus(AgentStatus.OFFLINE);
        when(agentRepository.findById("agent-1")).thenReturn(java.util.Optional.of(agent));
        when(agentRepository.save(agent)).thenReturn(agent);

        agentService.updateStatus("agent-1", new UpdateAgentStatusRequest(AgentStatus.OFFLINE));

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private Agent agent() {
        Agent agent = new Agent();
        agent.setId("agent-1");
        agent.setName("Agent One");
        agent.setStatus(AgentStatus.AVAILABLE);
        return agent;
    }
}
