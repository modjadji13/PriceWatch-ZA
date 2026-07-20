import { useSearchParams } from "react-router-dom";
import type { PriceOffer } from "./priceTypes";

export type PriceStatusFilter = "live" | "estimated" | "all";
export type SortOrder = "relevance" | "price";

export type ResultsFilters = {
  product: string;
  category: string;
  stores: string[];
  min: number | null;
  max: number | null;
  status: PriceStatusFilter;
  sort: SortOrder;
};

function numberOrNull(value: string | null) {
  if (value === null || value.trim() === "") {
    return null;
  }

  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}
//feat defaults to --live so any store that returned an estimate is invisible
//until a user changes to --all 
function statusFrom(value: string | null): PriceStatusFilter {
  return value === "estimated" || value === "live" ? value : "all";
}

function sortFrom(value: string | null): SortOrder {
  return value === "price" ? value : "relevance";
}

export function useResultsFilters() {
  const [searchParams, setSearchParams] = useSearchParams();

  const filters: ResultsFilters = {
    product: searchParams.get("product") ?? "",
    category: searchParams.get("category") ?? "GROCERY",
    stores: (searchParams.get("stores") ?? "")
      .split(",")
      .map((store) => store.trim())
      .filter(Boolean),
    min: numberOrNull(searchParams.get("min")),
    max: numberOrNull(searchParams.get("max")),
    status: statusFrom(searchParams.get("status")),
    sort: sortFrom(searchParams.get("sort")),
  };

  function updateFilters(next: Partial<Record<"stores" | "min" | "max" | "status" | "sort", string | null>>) {
    setSearchParams(
      (previous) => {
        const params = new URLSearchParams(previous);
        Object.entries(next).forEach(([key, value]) => {
          if (value === null || value === "") {
            params.delete(key);
          } else {
            params.set(key, value);
          }
        });
        return params;
      },
      { replace: true }
    );
  }

  return { filters, updateFilters };
}

export function applyFilters(offers: PriceOffer[], filters: ResultsFilters) {
  const filtered = offers.filter((offer) => {
    if (filters.status === "live" && offer.estimated) {
      return false;
    }
    if (filters.status === "estimated" && !offer.estimated) {
      return false;
    }
    if (filters.stores.length > 0 && !filters.stores.includes(offer.store)) {
      return false;
    }
    if (filters.min !== null && offer.amount < filters.min) {
      return false;
    }
    if (filters.max !== null && offer.amount > filters.max) {
      return false;
    }
    return true;
  });

  if (filters.sort === "price") {
    return [...filtered].sort((a, b) => a.amount - b.amount);
  }

  return filtered;
}

export function storeFacets(offers: PriceOffer[]) {
  const counts = new Map<string, number>();
  offers.forEach((offer) => {
    counts.set(offer.store, (counts.get(offer.store) ?? 0) + 1);
  });
  return [...counts.entries()]
    .map(([store, count]) => ({ store, count }))
    .sort((a, b) => a.store.localeCompare(b.store));
}
