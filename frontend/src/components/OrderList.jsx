export default function OrderList({ orders, pendingOrderIds, onSuggest, busyKey }) {
  if (!orders.length) return <p className="empty-state">No orders found.</p>;

  return (
    <div className="table-wrap">
      <table className="orders-table">
        <thead>
          <tr>
            <th>Order</th>
            <th>Description</th>
            <th>Assigned agent</th>
            <th>Status</th>
            <th aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => {
            const hasPendingSuggestion = pendingOrderIds.has(order.id);
            const isSuggesting = busyKey === `order:${order.id}`;
            return (
              <tr className={order.status === "REASSIGNMENT_PENDING" ? "row-pending" : ""} key={order.id}>
                <td><span className="order-id">{order.id}</span></td>
                <td className="order-description">{order.description}</td>
                <td>
                  {order.assignedAgentName || <span className="muted">Unassigned</span>}
                  {order.assignedAgentId && <span className="table-subline">{order.assignedAgentId}</span>}
                </td>
                <td><span className={`order-status ${order.status === "REASSIGNMENT_PENDING" ? "order-status-pending" : ""}`}>{order.status}</span></td>
                <td className="order-action">
                  {hasPendingSuggestion ? (
                    <span className="suggestion-exists">Suggestion pending</span>
                  ) : (
                    <button
                      className="button button-secondary button-small"
                      disabled={Boolean(busyKey)}
                      onClick={() => onSuggest(order.id)}
                    >
                      {isSuggesting ? "Working…" : "Suggest reassignment"}
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
