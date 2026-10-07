package com.ziprun.reassignment.repository;

import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ziprun.reassignment.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.entity.SuggestionStatus;
import com.ziprun.reassignment.entity.TriggerReason;

public interface SuggestionRepository extends JpaRepository<ReassignmentSuggestion, UUID> {

    List<ReassignmentSuggestion> findAllByOrderByIdDesc();

    List<ReassignmentSuggestion> findByStatusOrderByIdDesc(SuggestionStatus status);

    Optional<ReassignmentSuggestion> findFirstByOrder_IdAndStatusAndTriggerReason(
            String orderId,
            SuggestionStatus status,
            TriggerReason triggerReason);

    Optional<ReassignmentSuggestion> findFirstByOrder_IdAndStatusInAndTriggerReason(
            String orderId,
            Collection<SuggestionStatus> statuses,
            TriggerReason triggerReason);

    boolean existsByOrder_IdAndStatusAndTriggerReason(
            String orderId,
            SuggestionStatus status,
            TriggerReason triggerReason);

    default boolean existsPendingByOrderIdAndTriggerReason(String orderId, TriggerReason triggerReason) {
        return existsByOrder_IdAndStatusAndTriggerReason(
                orderId,
                SuggestionStatus.PENDING,
                triggerReason);
    }
}
