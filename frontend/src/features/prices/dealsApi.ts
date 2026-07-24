import { apiRequest } from "../../lib/apiClient";

export type Deal = {
  productId: number;
  title: string;
  category: string;
  currentPrice: number;
  oldPrice: number;
  discountPercent: number;
  store: string;
  storeCount: number;
  rangeLow: number;
  rangeHigh: number;
  imageUrl: string;
  storesCompared: string[];
  estimated: boolean;
};

// Real, history-derived deals for the home page. Returns [] when there is not
// enough recorded price history yet to detect a genuine drop.
export function getDeals() {
  return apiRequest<Deal[]>("/api/deals");
}
