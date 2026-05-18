import { BellPlus } from "lucide-react";
import { EmptyState } from "../../components/ui/EmptyState";
import { StatusBadge } from "../../components/ui/StatusBadge";

const sampleAlerts = [
  { id: 1, product: "Milk 2L", threshold: "R32.00", status: "Active" },
  { id: 2, product: "Coffee 750g", threshold: "R95.00", status: "Paused" },
];

export function AlertsPage() {
  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Private</p>
          <h1>Price alerts</h1>
          <p>Threshold-based alerts for saved products.</p>
        </div>
        <button type="button" className="primary-button">
          <BellPlus size={17} />
          New alert
        </button>
      </div>

      <div className="results-table-wrap">
        <table className="results-table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Target</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {sampleAlerts.map((alert) => (
              <tr key={alert.id}>
                <td>{alert.product}</td>
                <td>{alert.threshold}</td>
                <td>
                  <StatusBadge tone={alert.status === "Active" ? "green" : "amber"}>{alert.status}</StatusBadge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <EmptyState
        title="Alert delivery needs backend support"
        description="The frontend flow is ready for /api/alerts plus email or in-app notification delivery."
      />
    </section>
  );
}
