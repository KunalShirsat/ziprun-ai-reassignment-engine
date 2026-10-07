package com.ziprun.reassignment.dto;

import java.util.UUID;

import com.ziprun.reassignment.entity.SuggestionStatus;
import com.ziprun.reassignment.entity.TriggerReason;

public record SuggestionResponse(
        UUID id,
        String orderId,
        String recommendedAgentId,
        String recommendedAgentName,
        double confidence,
        String reasoning,
        SuggestionStatus status,
        TriggerReason triggerReason) {
}
