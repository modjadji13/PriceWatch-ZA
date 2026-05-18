import { apiRequest } from "../../lib/apiClient";
import { PriceComparison, PriceRecord } from "./priceTypes";

export function comparePrices(product: string, category: string) {
  return apiRequest<PriceComparison>("/api/prices/compare", {
    params: { product, category },
  });
}

export function getPriceHistory(productId: string) {
  return apiRequest<PriceRecord[]>("/api/prices/history", {
    params: { productId },
  });
}

export function getLowestPrice(productId: string) {
  return apiRequest<PriceRecord>("/api/prices/lowest", {
    params: { productId },
  });
}

export function getHighestPrice(productId: string) {
  return apiRequest<PriceRecord>("/api/prices/highest", {
    params: { productId },
  });
}
