package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.entity.AgentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAgentStatusRequest(
        @NotNull AgentStatus status) {
}
