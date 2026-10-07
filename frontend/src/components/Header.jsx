export default function Header({ agentCount, orderCount, pendingCount }) {
  return (
    <header className="page-header">
      <div className="header-copy">
        <div className="brand-mark" aria-hidden="true">Z</div>
        <div>
          <p className="eyebrow">OPERATIONS CONTROL CENTER</p>
          <h1>ZipRun Reassignment Engine</h1>
          <p className="subtitle">AI-powered delivery reassignment</p>
        </div>
      </div>
      <div className="header-summary" aria-label="Dashboard totals">
        <div><strong>{agentCount}</strong><span>Agents</span></div>
        <div><strong>{orderCount}</strong><span>Orders</span></div>
        <div className="summary-alert"><strong>{pendingCount}</strong><span>Pending</span></div>
      </div>
    </header>
  );
}
