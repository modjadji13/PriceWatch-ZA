import { apiRequest } from "../../lib/apiClient";

export type PriceAlert = {
  id: number;
  userEmail: string;
  productName: string;
  thresholdAmount: number;
  active: boolean;
  createdAt: string;
};

export type CreateAlertPayload = {
  userEmail: string;
  productName: string;
  thresholdAmount: number;
};

export function getAlerts(userEmail: string) {
  return apiRequest<PriceAlert[]>("/api/alerts", {
    params: { userEmail },
  });
}

export function createAlert(payload: CreateAlertPayload) {
  return apiRequest<PriceAlert>("/api/alerts", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function toggleAlert(id: number, userEmail: string) {
  return apiRequest<PriceAlert>(`/api/alerts/${id}/toggle`, {
    method: "PATCH",
    params: { userEmail },
  });
}

export function deleteAlert(id: number, userEmail: string) {
  return apiRequest<void>(`/api/alerts/${id}`, {
    method: "DELETE",
    params: { userEmail },
  });
}
