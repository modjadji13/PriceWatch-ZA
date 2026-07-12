import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellPlus, Trash2 } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { EmptyState } from "../../components/ui/EmptyState";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { formatCurrency } from "../../lib/formatCurrency";
import { useAuth } from "../auth/AuthProvider";
import { createAlert, deleteAlert, getAlerts, toggleAlert } from "./alertsApi";

export function AlertsPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const userEmail = user?.email ?? "";
  const [productName, setProductName] = useState(() => searchParams.get("product") ?? "");
  const [threshold, setThreshold] = useState("");

  const query = useQuery({
    queryKey: ["alerts", userEmail],
    queryFn: () => getAlerts(userEmail),
    enabled: userEmail.length > 0,
  });

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["alerts", userEmail] });
  }

  const createMutation = useMutation({
    mutationFn: createAlert,
    onSuccess: () => {
      setProductName("");
      setThreshold("");
      invalidate();
    },
  });

  const toggleMutation = useMutation({
    mutationFn: (id: number) => toggleAlert(id, userEmail),
    onSuccess: invalidate,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteAlert(id, userEmail),
    onSuccess: invalidate,
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = productName.trim();
    const amount = Number(threshold);
    if (!trimmed || !Number.isFinite(amount) || amount <= 0) {
      return;
    }

    createMutation.mutate({ userEmail, productName: trimmed, thresholdAmount: amount });
  }

  const alerts = query.data ?? [];

  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Private</p>
          <h1>Price alerts</h1>
          <p>Threshold-based alerts for saved products.</p>
        </div>
      </div>

      <form className="stacked-form" onSubmit={handleSubmit}>
        <label htmlFor="alert-product">Product</label>
        <input
          id="alert-product"
          value={productName}
          onChange={(event) => setProductName(event.target.value)}
          placeholder="e.g. Coffee 750g"
          required
        />

        <label htmlFor="alert-threshold">Alert me below (R)</label>
        <input
          id="alert-threshold"
          type="number"
          min="0.01"
          step="0.01"
          value={threshold}
          onChange={(event) => setThreshold(event.target.value)}
          placeholder="e.g. 95.00"
          required
        />

        <button type="submit" className="primary-button" disabled={createMutation.isPending}>
          <BellPlus size={17} />
          {createMutation.isPending ? "Creating..." : "New alert"}
        </button>
      </form>

      {query.isLoading ? <p>Loading alerts...</p> : null}
      {query.isError ? (
        <EmptyState
          title="Could not load your alerts"
          description="Make sure the backend is running on port 8081, then try again."
        />
      ) : null}

      {query.isSuccess && alerts.length === 0 ? (
        <EmptyState
          title="No alerts yet"
          description="Create an alert above and it will be saved to your account."
        />
      ) : null}

      {alerts.length > 0 ? (
        <div className="results-table-wrap">
          <table className="results-table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Target</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((alert) => (
                <tr key={alert.id}>
                  <td>{alert.productName}</td>
                  <td>{formatCurrency(alert.thresholdAmount)}</td>
                  <td>
                    <StatusBadge tone={alert.active ? "green" : "amber"}>
                      {alert.active ? "Active" : "Paused"}
                    </StatusBadge>
                  </td>
                  <td>
                    <button
                      type="button"
                      className="secondary-button compact"
                      onClick={() => toggleMutation.mutate(alert.id)}
                      disabled={toggleMutation.isPending}
                    >
                      {alert.active ? "Pause" : "Resume"}
                    </button>
                    <button
                      type="button"
                      className="icon-button"
                      aria-label={`Delete alert for ${alert.productName}`}
                      onClick={() => deleteMutation.mutate(alert.id)}
                      disabled={deleteMutation.isPending}
                    >
                      <Trash2 size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}
