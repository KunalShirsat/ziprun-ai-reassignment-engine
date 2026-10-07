package com.ziprun.reassignment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ziprun.reassignment.service.SuggestionGenerationService;

@Component
public class SuggestionGenerationEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(SuggestionGenerationEventHandler.class);

    private final SuggestionGenerationService suggestionGenerationService;

    public SuggestionGenerationEventHandler(SuggestionGenerationService suggestionGenerationService) {
        this.suggestionGenerationService = suggestionGenerationService;
    }

    @Async("virtualThreadTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SuggestionGenerationRequestedEvent event) {
        try {
            suggestionGenerationService.generate(event.suggestionId());
        } catch (RuntimeException exception) {
            logger.error(
                    "Suggestion generation failed for suggestion {}. Cause: {}",
                    event.suggestionId(),
                    exception.getClass().getSimpleName());
        }
    }
}
