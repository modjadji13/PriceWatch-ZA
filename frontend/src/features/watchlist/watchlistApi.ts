import { apiRequest } from "../../lib/apiClient";

export type WatchlistItem = {
  id: number;
  userEmail: string;
  productName: string;
  category: string;
  note: string | null;
  createdAt: string;
};

export type CreateWatchlistItemPayload = {
  userEmail: string;
  productName: string;
  category: string;
  note?: string;
};

export function getWatchlist(userEmail: string) {
  return apiRequest<WatchlistItem[]>("/api/watchlist", {
    params: { userEmail },
  });
}

export function addWatchlistItem(payload: CreateWatchlistItemPayload) {
  return apiRequest<WatchlistItem>("/api/watchlist", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function removeWatchlistItem(id: number, userEmail: string) {
  return apiRequest<void>(`/api/watchlist/${id}`, {
    method: "DELETE",
    params: { userEmail },
  });
}
