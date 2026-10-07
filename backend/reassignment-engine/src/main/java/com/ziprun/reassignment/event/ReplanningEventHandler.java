package com.ziprun.reassignment.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.ziprun.reassignment.service.ReplanningService;

@Component
public class ReplanningEventHandler {

    private final ReplanningService replanningService;

    public ReplanningEventHandler(ReplanningService replanningService) {
        this.replanningService = replanningService;
    }

    @Async("virtualThreadTaskExecutor")
    @EventListener
    public void handleAgentOffline(AgentOfflineEvent event) {
        replanningService.replanOrdersForOfflineAgent(event.agentId());
    }
}
