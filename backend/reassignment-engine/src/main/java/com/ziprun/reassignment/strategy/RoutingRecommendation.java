package com.ziprun.reassignment.strategy;

import com.ziprun.reassignment.entity.Agent;

public record RoutingRecommendation(Agent agent, double confidence, String reasoning) {
}
