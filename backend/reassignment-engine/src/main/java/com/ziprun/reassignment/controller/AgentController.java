package com.ziprun.reassignment.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ziprun.reassignment.dto.AgentResponse;
import com.ziprun.reassignment.dto.UpdateAgentStatusRequest;
import com.ziprun.reassignment.service.AgentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PatchMapping("/{id}/status")
    public AgentResponse updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateAgentStatusRequest request) {
        return agentService.updateStatus(id, request);
    }

    @GetMapping
    public List<AgentResponse> getAgents() {
        return agentService.getAgents();
    }
}
