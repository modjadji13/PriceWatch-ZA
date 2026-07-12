import { Award, Bell, ChartNoAxesColumnIncreasing, Shield, X } from "lucide-react";
import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import { comparePrices } from "../../features/prices/priceApi";
import { categories } from "../../features/prices/priceTypes";
import { applyFilters, storeFacets, useResultsFilters } from "../../features/prices/useResultsFilters";

export function FilterSidebar() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { filters, updateFilters } = useResultsFilters();
  const hasSearch = filters.product.trim().length > 0;

  const query = useQuery({
    queryKey: ["price-compare", filters.product, filters.category],
    queryFn: () => comparePrices(filters.product, filters.category),
    enabled: hasSearch,
  });

  const offers = query.data?.prices ?? [];
  const facets = storeFacets(offers);
  const visibleCount = applyFilters(offers, filters).length;
  const highestAmount = offers.reduce((max, offer) => Math.max(max, offer.amount), 0);
  const sliderMax = Math.max(100, Math.ceil(highestAmount / 100) * 100);

  const activeChips: { label: string; onRemove: () => void }[] = [];
  if (hasSearch) {
    activeChips.push({
      label: filters.product,
      onRemove: () => navigate("/"),
    });
  }
  if (filters.stores.length > 0) {
    activeChips.push({
      label: `${filters.stores.length} store${filters.stores.length === 1 ? "" : "s"}`,
      onRemove: () => updateFilters({ stores: null }),
    });
  }
  if (filters.min !== null || filters.max !== null) {
    activeChips.push({
      label: `R${filters.min ?? 0} - R${filters.max ?? "any"}`,
      onRemove: () => updateFilters({ min: null, max: null }),
    });
  }
  if (filters.status !== "live") {
    activeChips.push({
      label: filters.status === "all" ? "Live + estimated" : "Estimated only",
      onRemove: () => updateFilters({ status: null }),
    });
  }

  function toggleStore(store: string) {
    const selected = filters.stores.length > 0 ? filters.stores : facets.map((facet) => facet.store);
    const next = selected.includes(store)
      ? selected.filter((item) => item !== store)
      : [...selected, store];

    if (next.length === 0 || next.length === facets.length) {
      updateFilters({ stores: null });
      return;
    }

    updateFilters({ stores: next.join(",") });
  }

  function setStatus(live: boolean, estimated: boolean) {
    if (!live && !estimated) {
      return;
    }

    updateFilters({ status: live && estimated ? "all" : estimated ? "estimated" : null });
  }

  const liveChecked = filters.status === "live" || filters.status === "all";
  const estimatedChecked = filters.status === "estimated" || filters.status === "all";

  return (
    <aside className="filter-sidebar" aria-label="Product filters">
      <div className="filter-panel">
        {hasSearch ? (
          <>
            <FilterGroup title="Active Filters">
              {activeChips.length > 0 ? (
                <div className="filter-tags">
                  {activeChips.map((chip) => (
                    <span key={chip.label}>
                      {chip.label}
                      <button type="button" aria-label={`Remove filter ${chip.label}`} onClick={chip.onRemove}>
                        <X size={12} />
                      </button>
                    </span>
                  ))}
                </div>
              ) : (
                <p className="filter-hint">No filters applied.</p>
              )}
            </FilterGroup>

            <FilterGroup title="Categories">
              {categories.map((category) => (
                <label className="check-row" key={category}>
                  <input
                    type="checkbox"
                    checked={filters.category === category}
                    onChange={() =>
                      navigate(
                        `/results?product=${encodeURIComponent(filters.product)}&category=${encodeURIComponent(category)}`
                      )
                    }
                  />
                  <span>{category.charAt(0) + category.slice(1).toLowerCase()}</span>
                </label>
              ))}
            </FilterGroup>

            <FilterGroup title="Stores">
              {facets.length === 0 ? (
                <p className="filter-hint">
                  {query.isLoading ? "Checking store prices..." : "No store offers for this search yet."}
                </p>
              ) : (
                facets.map((facet) => (
                  <label className="check-row" key={facet.store}>
                    <input
                      type="checkbox"
                      checked={filters.stores.length === 0 || filters.stores.includes(facet.store)}
                      onChange={() => toggleStore(facet.store)}
                    />
                    <span>{facet.store}</span>
                    <small>{facet.count}</small>
                  </label>
                ))
              )}
            </FilterGroup>

            <FilterGroup title="Price Range" meta={`R${filters.min ?? 0} - R${filters.max ?? sliderMax}`}>
              <input
                className="range-input"
                type="range"
                min="0"
                max={sliderMax}
                value={filters.max ?? sliderMax}
                onChange={(event) =>
                  updateFilters({ max: Number(event.target.value) >= sliderMax ? null : event.target.value })
                }
              />
              <div className="price-inputs">
                <label>
                  <span>R</span>
                  <input
                    type="number"
                    min="0"
                    placeholder="Min"
                    value={filters.min ?? ""}
                    onChange={(event) => updateFilters({ min: event.target.value })}
                  />
                </label>
                <label>
                  <span>R</span>
                  <input
                    type="number"
                    min="0"
                    placeholder="Max"
                    value={filters.max ?? ""}
                    onChange={(event) => updateFilters({ max: event.target.value })}
                  />
                </label>
              </div>
            </FilterGroup>

            <FilterGroup title="Price Status" meta={query.isSuccess ? `${visibleCount} shown` : undefined}>
              <label className="check-row">
                <input
                  type="checkbox"
                  checked={liveChecked}
                  onChange={(event) => setStatus(event.target.checked, estimatedChecked)}
                />
                <span className="status-dot green" />
                <span>Live price</span>
              </label>
              <label className="check-row">
                <input
                  type="checkbox"
                  checked={estimatedChecked}
                  onChange={(event) => setStatus(liveChecked, event.target.checked)}
                />
                <span className="status-dot yellow" />
                <span>Estimated price</span>
              </label>
            </FilterGroup>
          </>
        ) : (
          <FilterGroup title="Filters">
            <p className="filter-hint">
              <Award size={13} /> Search for a product to filter results by store, price range, and price status.
            </p>
          </FilterGroup>
        )}

        <FilterGroup title="Account">
          <Link to="/app/watchlist" className="sidebar-link">
            <ChartNoAxesColumnIncreasing size={14} />
            Watchlist
          </Link>
          <Link to="/app/alerts" className="sidebar-link">
            <Bell size={14} />
            Alerts
          </Link>
          {user?.role === "ADMIN" && (
            <Link to="/admin" className="sidebar-link">
              <Shield size={14} />
              Admin
            </Link>
          )}
        </FilterGroup>
      </div>
    </aside>
  );
}

function FilterGroup({ title, meta, children }: { title: string; meta?: string; children: ReactNode }) {
  return (
    <section className="filter-group">
      <div className="filter-title-row">
        <h3>{title}</h3>
        {meta && <span>{meta}</span>}
      </div>
      <div className="filter-content">{children}</div>
    </section>
  );
}
