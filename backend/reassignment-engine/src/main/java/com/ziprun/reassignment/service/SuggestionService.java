package com.ziprun.reassignment.service;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ziprun.reassignment.dto.SuggestionResponse;
import com.ziprun.reassignment.dto.UpdateSuggestionStatusRequest;
import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.OrderStatus;
import com.ziprun.reassignment.entity.RecommendationSource;
import com.ziprun.reassignment.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.entity.SuggestionStatus;
import com.ziprun.reassignment.entity.TriggerReason;
import com.ziprun.reassignment.event.SuggestionGenerationRequestedEvent;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.repository.SuggestionRepository;

@Service
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SuggestionService(
            SuggestionRepository suggestionRepository,
            OrderRepository orderRepository,
            ApplicationEventPublisher eventPublisher) {
        this.suggestionRepository = suggestionRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SuggestionResponse requestInitialSuggestion(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found: " + orderId));

        Optional<ReassignmentSuggestion> existingSuggestion =
                suggestionRepository.findFirstByOrder_IdAndStatusInAndTriggerReason(
                        orderId, List.of(SuggestionStatus.PROCESSING, SuggestionStatus.PENDING), TriggerReason.INITIAL);
        if (existingSuggestion.isPresent()) {
            return toResponse(existingSuggestion.get());
        }

        ReassignmentSuggestion suggestion = new ReassignmentSuggestion();
        suggestion.setOrder(order);
        suggestion.setRecommendedAgent(null);
        suggestion.setConfidence(0.0);
        suggestion.setReasoning("Generating recommendation.");
        suggestion.setStatus(SuggestionStatus.PROCESSING);
        suggestion.setTriggerReason(TriggerReason.INITIAL);
        ReassignmentSuggestion savedSuggestion = suggestionRepository.saveAndFlush(suggestion);
        eventPublisher.publishEvent(new SuggestionGenerationRequestedEvent(savedSuggestion.getId()));

        return toResponse(savedSuggestion);
    }

    @Transactional
    public SuggestionResponse createSuggestion(
            Order order,
            Agent recommendedAgent,
            double confidence,
            String reasoning,
            TriggerReason triggerReason,
            RecommendationSource recommendationSource) {
        Optional<SuggestionResponse> existingSuggestion =
                findPendingSuggestion(order.getId(), triggerReason);
        if (existingSuggestion.isPresent()) {
            return existingSuggestion.get();
        }

        ReassignmentSuggestion suggestion = new ReassignmentSuggestion();
        suggestion.setOrder(order);
        suggestion.setRecommendedAgent(recommendedAgent);
        suggestion.setConfidence(confidence);
        suggestion.setReasoning(reasoning);
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setTriggerReason(triggerReason);
        suggestion.setRecommendationSource(recommendationSource);

        return toResponse(suggestionRepository.save(suggestion));
    }

    @Transactional(readOnly = true)
    public Optional<SuggestionResponse> findPendingSuggestion(String orderId, TriggerReason triggerReason) {
        return suggestionRepository.findFirstByOrder_IdAndStatusAndTriggerReason(
                        orderId,
                        SuggestionStatus.PENDING,
                        triggerReason)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> getSuggestions(SuggestionStatus status) {
        List<ReassignmentSuggestion> suggestions = status == null
                ? suggestionRepository.findAllByOrderByIdDesc()
                : suggestionRepository.findByStatusOrderByIdDesc(status);
        return suggestions.stream().map(this::toResponse).toList();
    }

    @Transactional
    public SuggestionResponse updateSuggestionStatus(
            UUID suggestionId,
            UpdateSuggestionStatusRequest request) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Suggestion not found: " + suggestionId));

        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            if (suggestion.getStatus() == SuggestionStatus.PROCESSING) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Suggestion is still being generated");
            }
            throw new IllegalStateException(
                    "Suggestion is already processed: " + suggestionId);
        }

        SuggestionStatus requestedStatus = request.status();
        if (requestedStatus == SuggestionStatus.REJECTED) {
            suggestion.setStatus(SuggestionStatus.REJECTED);
            return toResponse(suggestionRepository.save(suggestion));
        }

        if (requestedStatus != SuggestionStatus.ACCEPTED) {
            throw new IllegalArgumentException("Suggestion status must be ACCEPTED or REJECTED");
        }

        suggestion.setStatus(SuggestionStatus.ACCEPTED);
        Order order = suggestion.getOrder();
        Agent previousAgent = order.getAssignedAgent();
        Agent recommendedAgent = suggestion.getRecommendedAgent();
        if (!previousAgent.getId().equals(recommendedAgent.getId())) {
            previousAgent.setActiveOrderCount(previousAgent.getActiveOrderCount() - 1);
            recommendedAgent.setActiveOrderCount(recommendedAgent.getActiveOrderCount() + 1);
        }
        order.setAssignedAgent(recommendedAgent);
        order.setStatus(OrderStatus.REASSIGNED);
        orderRepository.save(order);

        return toResponse(suggestionRepository.save(suggestion));
    }

    private SuggestionResponse toResponse(ReassignmentSuggestion suggestion) {
        Agent recommendedAgent = suggestion.getRecommendedAgent();
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getOrder().getId(),
                recommendedAgent == null ? null : recommendedAgent.getId(),
                recommendedAgent == null ? null : recommendedAgent.getName(),
                suggestion.getConfidence(),
                suggestion.getReasoning(),
                suggestion.getStatus(),
                suggestion.getTriggerReason(),
                suggestion.getRecommendationSource());
    }
}
