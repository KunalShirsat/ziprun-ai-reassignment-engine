const STATUSES = ["AVAILABLE", "BUSY", "OFFLINE"];

export default function AgentList({ agents, onStatusChange, busyKey }) {
  if (!agents.length) return <p className="empty-state">No agents found.</p>;

  return (
    <div className="agent-grid">
      {agents.map((agent) => (
        <article className="agent-card" key={agent.id}>
          <div className="agent-card-top">
            <div className="avatar">{agent.name?.charAt(0)?.toUpperCase() || "A"}</div>
            <span className={`status-badge status-${agent.status?.toLowerCase()}`}>
              <span className="status-dot" />{agent.status}
            </span>
          </div>
          <div className="agent-identity">
            <h3>{agent.name}</h3>
            <span className="agent-id">{agent.id}</span>
          </div>
          <div className="agent-load">
            <span>Active orders</span>
            <strong>{agent.activeOrderCount}</strong>
          </div>
          <label className="status-control">
            <span>Update status</span>
            <select
              aria-label={`Update ${agent.name} status`}
              value={agent.status}
              disabled={busyKey === `agent:${agent.id}`}
              onChange={(event) => onStatusChange(agent.id, event.target.value)}
            >
              {STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
            </select>
          </label>
        </article>
      ))}
    </div>
  );
}
