package com.ziprun.reassignment.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ziprun.reassignment.dto.RoutingStrategyResponse;
import com.ziprun.reassignment.dto.UpdateRoutingStrategyRequest;
import com.ziprun.reassignment.strategy.RoutingEngine;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/routing")
public class RoutingController {

    private final RoutingEngine routingEngine;

    public RoutingController(RoutingEngine routingEngine) {
        this.routingEngine = routingEngine;
    }

    @GetMapping("/strategy")
    public RoutingStrategyResponse getStrategy() {
        return new RoutingStrategyResponse(routingEngine.getActiveStrategyName());
    }

    @PatchMapping("/strategy")
    public RoutingStrategyResponse updateStrategy(
            @Valid @RequestBody UpdateRoutingStrategyRequest request) {
        return new RoutingStrategyResponse(routingEngine.switchStrategy(request.strategy()));
    }
}
