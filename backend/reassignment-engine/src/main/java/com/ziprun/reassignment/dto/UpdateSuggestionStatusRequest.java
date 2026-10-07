package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.entity.SuggestionStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSuggestionStatusRequest(
        @NotNull SuggestionStatus status) {
}
