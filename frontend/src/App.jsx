import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import AgentList from "./components/AgentList.jsx";
import Header from "./components/Header.jsx";
import OrderList from "./components/OrderList.jsx";
import SuggestionList from "./components/SuggestionList.jsx";
import { api } from "./services/api.js";

const POLL_INTERVAL_MS = 4000;

export default function App() {
  const [agents, setAgents] = useState([]);
  const [orders, setOrders] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busyKey, setBusyKey] = useState("");
  const refreshInProgress = useRef(false);

  const refreshData = useCallback(async (showLoading = false) => {
    if (refreshInProgress.current) return;
    refreshInProgress.current = true;
    if (showLoading) setLoading(true);
    try {
      const results = await Promise.allSettled([
        api.getAgents(),
        api.getOrders(),
        api.getActiveSuggestions(),
      ]);
      const [agentsResult, ordersResult, suggestionsResult] = results;
      if (agentsResult.status === "fulfilled") setAgents(agentsResult.value);
      if (ordersResult.status === "fulfilled") setOrders(ordersResult.value);
      if (suggestionsResult.status === "fulfilled") setSuggestions(suggestionsResult.value);

      const failedResults = results.filter((result) => result.status === "rejected");
      setError(failedResults.length
        ? failedResults.map((result) => result.reason?.message || "A dashboard request failed.").join(" ")
        : "");
    } finally {
      refreshInProgress.current = false;
      if (showLoading) setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshData(true);
    const intervalId = window.setInterval(() => refreshData(), POLL_INTERVAL_MS);
    return () => window.clearInterval(intervalId);
  }, [refreshData]);

  const pendingOrderIds = useMemo(
    () => new Set(suggestions.map((suggestion) => suggestion.orderId)),
    [suggestions],
  );
  const pendingSuggestionCount = useMemo(
    () => suggestions.filter((suggestion) => suggestion.status === "PENDING").length,
    [suggestions],
  );

  async function changeAgentStatus(agentId, status) {
    const key = `agent:${agentId}`;
    setBusyKey(key);
    setError("");
    try {
      const updatedAgent = await api.updateAgentStatus(agentId, status);
      setAgents((current) => current.map((agent) => agent.id === agentId ? updatedAgent : agent));
      await refreshData();
    } catch (actionError) {
      setError(actionError.message || "Could not update agent status.");
    } finally {
      setBusyKey("");
    }
  }

  async function suggestReassignment(orderId) {
    setBusyKey(`order:${orderId}`);
    setError("");
    try {
      await api.suggestReassignment(orderId);
      await refreshData();
    } catch (actionError) {
      setError(actionError.message || "Could not generate a suggestion.");
    } finally {
      setBusyKey("");
    }
  }

  async function updateSuggestion(suggestionId, status) {
    setBusyKey(`suggestion:${suggestionId}:${status}`);
    setError("");
    try {
      await api.updateSuggestionStatus(suggestionId, status);
      await refreshData();
    } catch (actionError) {
      setError(actionError.message || `Could not ${status.toLowerCase()} suggestion.`);
    } finally {
      setBusyKey("");
    }
  }

  return (
    <main className="dashboard">
      <Header agentCount={agents.length} orderCount={orders.length} pendingCount={pendingSuggestionCount} />

      {error && (
        <div className="error-banner" role="alert">
          <span>{error}</span>
          <button className="text-button" onClick={() => refreshData(true)}>Retry</button>
        </div>
      )}

      {loading ? (
        <div className="loading-state" role="status"><span className="loader" />Loading operations data…</div>
      ) : (
        <>
          <section className="section" aria-labelledby="agents-title">
            <div className="section-heading">
              <div><p className="eyebrow">TEAM OVERVIEW</p><h2 id="agents-title">Agent Status</h2></div>
              <span className="section-count">{agents.length} agents</span>
            </div>
            <AgentList agents={agents} onStatusChange={changeAgentStatus} busyKey={busyKey} />
          </section>

          <section className="section" aria-labelledby="orders-title">
            <div className="section-heading">
              <div><p className="eyebrow">LIVE DISPATCH</p><h2 id="orders-title">Orders</h2></div>
              <span className="section-count">{orders.length} orders</span>
            </div>
            <OrderList
              orders={orders}
              pendingOrderIds={pendingOrderIds}
              onSuggest={suggestReassignment}
              busyKey={busyKey}
            />
          </section>

          <section className="section" aria-labelledby="suggestions-title">
            <div className="section-heading">
              <div><p className="eyebrow">REVIEW & DECIDE</p><h2 id="suggestions-title">Pending Reassignment Suggestions</h2></div>
              <span className={`section-count ${pendingSuggestionCount ? "count-attention" : ""}`}>
                {pendingSuggestionCount} pending
              </span>
            </div>
            <SuggestionList
              suggestions={suggestions}
              onDecision={updateSuggestion}
              busyKey={busyKey}
            />
          </section>
          <footer className="dashboard-footer">
            <span><span className="live-dot" /> Live updates every 4 seconds</span>
            <span>ZipRun Operations</span>
          </footer>
        </>
      )}
    </main>
  );
}
