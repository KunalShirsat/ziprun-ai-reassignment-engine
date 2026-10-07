package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.entity.OrderStatus;

public record OrderResponse(
        String id,
        String description,
        String assignedAgentId,
        String assignedAgentName,
        OrderStatus status) {
}
