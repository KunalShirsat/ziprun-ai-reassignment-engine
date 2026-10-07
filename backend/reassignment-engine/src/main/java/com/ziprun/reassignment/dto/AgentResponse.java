package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.entity.AgentStatus;

public record AgentResponse(
        String id,
        String name,
        int activeOrderCount,
        AgentStatus status) {
}
