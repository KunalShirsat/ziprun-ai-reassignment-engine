function triggerLabel(triggerReason) {
  return triggerReason === "AGENT_OFFLINE" ? "AGENT OFFLINE" : triggerReason;
}

export default function SuggestionList({ suggestions, onDecision, busyKey }) {
  if (!suggestions.length) {
    return (
      <div className="empty-state suggestion-empty">
        <span className="empty-icon" aria-hidden="true">✓</span>
        <strong>All clear</strong>
        <span>No pending reassignment suggestions.</span>
      </div>
    );
  }

  return (
    <div className="suggestion-grid">
      {suggestions.map((suggestion) => {
        const isProcessing = suggestion.status === "PROCESSING";
        const isAccepting = busyKey === `suggestion:${suggestion.id}:ACCEPTED`;
        const isRejecting = busyKey === `suggestion:${suggestion.id}:REJECTED`;
        return (
          <article className="suggestion-card" key={suggestion.id}>
            <div className="suggestion-heading">
              <div>
                <p className="eyebrow">
                  {isProcessing ? "GENERATING RECOMMENDATION" : "REASSIGNMENT RECOMMENDED"}
                </p>
                <h3>Order <span>{suggestion.orderId}</span></h3>
              </div>
              <span className={`trigger-badge ${suggestion.triggerReason === "AGENT_OFFLINE" ? "trigger-offline" : "trigger-initial"}`}>
                {triggerLabel(suggestion.triggerReason)}
              </span>
            </div>
            {isProcessing ? (
              <div className="generation-state" role="status">
                <span className="loader" />
                <span>Generating recommendation… This suggestion is not ready for approval.</span>
              </div>
            ) : (
              <>
                <div className="recommendation">
                  <div>
                    <span className="detail-label">Recommended agent</span>
                    <strong>{suggestion.recommendedAgentName || suggestion.recommendedAgentId}</strong>
                    <span className="table-subline">{suggestion.recommendedAgentId}</span>
                  </div>
                  <div className="confidence">
                    <strong>{Math.round(suggestion.confidence * 100)}%</strong>
                    <span>confidence</span>
                  </div>
                </div>
                <div className="reasoning">
                  <span className="detail-label">Reasoning</span>
                  <p>{suggestion.reasoning}</p>
                </div>
              </>
            )}
            <div className="suggestion-footer">
              <span className="pending-label"><span className="status-dot" />{suggestion.status}</span>
              {!isProcessing && (
                <div className="decision-actions">
                  <button
                    className="button button-reject"
                    disabled={Boolean(busyKey)}
                    onClick={() => onDecision(suggestion.id, "REJECTED")}
                  >
                    {isRejecting ? "Rejecting…" : "Reject"}
                  </button>
                  <button
                    className="button button-accept"
                    disabled={Boolean(busyKey)}
                    onClick={() => onDecision(suggestion.id, "ACCEPTED")}
                  >
                    {isAccepting ? "Accepting…" : "Accept"}
                  </button>
                </div>
              )}
            </div>
          </article>
        );
      })}
    </div>
  );
}
