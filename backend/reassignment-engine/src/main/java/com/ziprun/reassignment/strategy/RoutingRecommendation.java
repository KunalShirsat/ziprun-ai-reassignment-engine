package com.ziprun.reassignment.strategy;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.RecommendationSource;

public record RoutingRecommendation(
        Agent agent,
        double confidence,
        String reasoning,
        RecommendationSource source) {
}
