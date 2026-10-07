const API_BASE_URL = import.meta.env.DEV
  ? "/backend-api"
  : (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080");

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: {
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...options.headers,
      },
    });
  } catch (error) {
    throw new Error(
      `Could not connect to the backend at ${API_BASE_URL}. Check that it is running and allows browser requests (CORS).`,
      { cause: error },
    );
  }

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(detail || `Backend request failed with status ${response.status}`);
  }

  if (response.status === 204) return null;
  return response.json();
}

export const api = {
  getAgents: () => request("/agents"),
  getOrders: () => request("/orders"),
  getRoutingStrategy: () => request("/routing/strategy"),
  updateRoutingStrategy: (strategy) =>
    request("/routing/strategy", {
      method: "PATCH",
      body: JSON.stringify({ strategy }),
    }),
  getActiveSuggestions: async () => {
    const suggestions = await request("/suggestions");
    return suggestions.filter(
      (suggestion) => suggestion.status === "PROCESSING" || suggestion.status === "PENDING",
    );
  },
  suggestReassignment: (orderId) =>
    request(`/orders/${encodeURIComponent(orderId)}/suggest`, { method: "POST" }),
  updateAgentStatus: (agentId, status) =>
    request(`/agents/${encodeURIComponent(agentId)}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }),
  updateSuggestionStatus: (suggestionId, status) =>
    request(`/suggestions/${encodeURIComponent(suggestionId)}`, {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }),
};
