package com.ziprun.reassignment.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoutingStrategyRequest(
        @NotBlank String strategy) {
}
