package com.ziprun.reassignment.controller;

import java.util.UUID;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ziprun.reassignment.dto.SuggestionResponse;
import com.ziprun.reassignment.dto.UpdateSuggestionStatusRequest;
import com.ziprun.reassignment.entity.SuggestionStatus;
import com.ziprun.reassignment.service.SuggestionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public List<SuggestionResponse> getSuggestions(
            @RequestParam(required = false) SuggestionStatus status) {
        return suggestionService.getSuggestions(status);
    }

    @PatchMapping("/{id}")
    public SuggestionResponse updateSuggestionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSuggestionStatusRequest request) {
        return suggestionService.updateSuggestionStatus(id, request);
    }
}
