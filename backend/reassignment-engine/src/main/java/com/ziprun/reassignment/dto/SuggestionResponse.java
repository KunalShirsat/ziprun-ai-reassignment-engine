package com.ziprun.reassignment.dto;

import java.util.UUID;

import com.ziprun.reassignment.entity.SuggestionStatus;
import com.ziprun.reassignment.entity.RecommendationSource;
import com.ziprun.reassignment.entity.TriggerReason;

public record SuggestionResponse(
        UUID id,
        String orderId,
        String recommendedAgentId,
        String recommendedAgentName,
        double confidence,
        String reasoning,
        SuggestionStatus status,
        TriggerReason triggerReason,
        RecommendationSource recommendationSource) {

    public SuggestionResponse(
            UUID id,
            String orderId,
            String recommendedAgentId,
            String recommendedAgentName,
            double confidence,
            String reasoning,
            SuggestionStatus status,
            TriggerReason triggerReason) {
        this(id, orderId, recommendedAgentId, recommendedAgentName, confidence, reasoning,
                status, triggerReason, null);
    }
}
