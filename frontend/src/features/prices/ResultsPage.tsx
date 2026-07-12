import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent, SyntheticEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Bell, BookmarkPlus, Check, Heart, LayoutGrid, List, RefreshCw, Search } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { formatCurrency } from "../../lib/formatCurrency";
import { escapeSvgText } from "../../lib/svg";
import { useAuth } from "../auth/AuthProvider";
import { addWatchlistItem } from "../watchlist/watchlistApi";
import { comparePrices } from "./priceApi";
import { categories } from "./priceTypes";
import type { PriceOffer } from "./priceTypes";
import { productFallbackFor } from "./productFallbacks";
import { applyFilters, useResultsFilters } from "./useResultsFilters";

const RESULT_POLL_INTERVAL_MS = 2_000;
const RESULT_POLL_WINDOW_MS = 60_000;

export function ResultsPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { filters, updateFilters } = useResultsFilters();
  const product = filters.product;
  const category = filters.category;
  const [searchProduct, setSearchProduct] = useState(product);
  const [searchCategory, setSearchCategory] = useState(category);
  const [view, setView] = useState<"grid" | "list">("grid");
  const [savedRows, setSavedRows] = useState<Set<string>>(new Set());
  const searchInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    function handleShortcut(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        searchInputRef.current?.focus();
        searchInputRef.current?.select();
      }
    }

    window.addEventListener("keydown", handleShortcut);
    return () => window.removeEventListener("keydown", handleShortcut);
  }, []);

  // Right after a search the backend is still enriching the stored result in
  // the background (slow stores land within its ~45s scrape budget), so poll
  // every 2s while that can change the answer, then stop. Polled reads are
  // cheap: the backend serves them straight from the database.
  const pollStartedAtRef = useRef(Date.now());

  useEffect(() => {
    pollStartedAtRef.current = Date.now();
  }, [product, category]);

  const query = useQuery({
    queryKey: ["price-compare", product, category],
    queryFn: () => comparePrices(product, category),
    enabled: product.trim().length > 0,
    refetchInterval: () =>
      Date.now() - pollStartedAtRef.current < RESULT_POLL_WINDOW_MS ? RESULT_POLL_INTERVAL_MS : false,
  });

  const rows = useMemo(() => {
    return applyFilters(query.data?.prices ?? [], filters);
  }, [query.data, filters]);

  const lowestAmount = rows.length > 0 ? Math.min(...rows.map((row) => row.amount)) : null;

  const details = query.data?.details;
  const productFallback = productFallbackFor(product);
  const displayProductName = productFallback?.name ?? fullProductName(details?.name, details?.description, product);
  const displayCategory = productFallback?.category ?? category;
  const genericImageUrl = fallbackProductImageUrl(displayProductName, displayCategory);
  const productImageUrl = details?.imageUrl || productFallback?.imageUrl || genericImageUrl;

  const saveMutation = useMutation({
    mutationFn: addWatchlistItem,
    onSuccess: (item) => {
      setSavedRows((previous) => new Set(previous).add(item.productName));
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = searchProduct.trim();
    if (!trimmed) {
      return;
    }

    navigate(`/results?product=${encodeURIComponent(trimmed)}&category=${encodeURIComponent(searchCategory)}`);
  }

  function loginRedirect() {
    navigate("/login", { state: { from: `/results?product=${product}&category=${category}` } });
  }

  function saveProduct(name: string, productCategory: string, note?: string) {
    if (!user) {
      loginRedirect();
      return;
    }

    saveMutation.mutate({
      userEmail: user.email,
      productName: name,
      category: productCategory,
      note,
    });
  }

  function createAlert(name: string) {
    if (!user) {
      loginRedirect();
      return;
    }

    navigate(`/app/alerts?product=${encodeURIComponent(name)}`);
  }

  if (!product) {
    return (
      <section className="dashboard-empty">
        <h1>Search a product first</h1>
        <p>Start from the search page to compare prices across stores.</p>
        <Link to="/" className="primary-button inline-button">
          Search prices
        </Link>
      </section>
    );
  }

  return (
    <section className="dashboard-page">
      <div className="search-toolbar">
        <div className="toolbar-row">
          <form className="toolbar-search" onSubmit={handleSubmit}>
            <Search size={16} />
            <input
              ref={searchInputRef}
              value={searchProduct}
              onChange={(event) => setSearchProduct(event.target.value)}
              placeholder="Search products across all stores..."
            />
            <kbd>Ctrl K</kbd>
          </form>

          <div className="category-select">
            <select
              value={searchCategory}
              onChange={(event) => setSearchCategory(event.target.value)}
              aria-label="Category"
            >
              {categories.map((item) => (
                <option key={item} value={item}>
                  {item.toLowerCase()}
                </option>
              ))}
            </select>
          </div>

          <div className="sort-pills">
            <span>Sort by:</span>
            <button
              type="button"
              className={filters.sort === "relevance" ? "active" : undefined}
              onClick={() => updateFilters({ sort: null })}
            >
              Relevance
            </button>
            <button
              type="button"
              className={filters.sort === "price" ? "active" : undefined}
              onClick={() => updateFilters({ sort: "price" })}
            >
              Lowest Price
            </button>
          </div>
        </div>

        <div className="result-count-row">
          <span>
            Showing <strong>{rows.length ? `1-${rows.length}` : "0"}</strong> of <strong>{rows.length}</strong> results
            for "{product}"
          </span>
          <div>
            <button type="button" onClick={() => query.refetch()} aria-label="Refresh prices">
              <RefreshCw size={16} />
            </button>
            <button
              type="button"
              aria-label="Grid view"
              className={view === "grid" ? "active" : undefined}
              onClick={() => setView("grid")}
            >
              <LayoutGrid size={16} />
            </button>
            <button
              type="button"
              aria-label="List view"
              className={view === "list" ? "active" : undefined}
              onClick={() => setView("list")}
            >
              <List size={16} />
            </button>
          </div>
        </div>
      </div>

      <div className="product-grid-shell">
        {query.isLoading && <div className="loading-block">Checking store prices...</div>}

        {query.isError && (
          <div className="error-block">
            The price service is not reachable yet. Start the Spring backend on port 8081 and try again.
          </div>
        )}

        {!query.isLoading && !query.isError && rows.length === 0 && (
          <div className="empty-state">
            <h2>No prices found</h2>
            <p>Try a different product name or category, or loosen the sidebar filters.</p>
          </div>
        )}

        {rows.length > 0 && (
          <>
            <div className="action-strip">
              <button
                type="button"
                className="secondary-button"
                onClick={() => saveProduct(displayProductName, displayCategory, `Found from search "${product}"`)}
                disabled={saveMutation.isPending || savedRows.has(displayProductName)}
              >
                {savedRows.has(displayProductName) ? <Check size={17} /> : <BookmarkPlus size={17} />}
                {savedRows.has(displayProductName) ? "Saved to watchlist" : "Save to watchlist"}
              </button>
              <button type="button" className="secondary-button" onClick={() => createAlert(displayProductName)}>
                <Bell size={17} />
                Create alert
              </button>
            </div>

            <div className={view === "list" ? "product-grid list-view" : "product-grid"}>
              {rows.map((row, index) => {
                const rowName = row.productName || displayProductName;
                const rowCategory = row.productCategory || displayCategory;
                const isSaved = savedRows.has(rowName);

                return (
                  <article className="product-card" key={`${rowName}-${row.store}-${index}`}>
                    <div className="product-art">
                      <img
                        className="result-product-image"
                        src={row.productImageUrl || productImageUrl}
                        alt={rowName}
                        onError={(event) =>
                          replaceBrokenImage(
                            event,
                            row.productImageUrl
                              ? fallbackProductImageUrl(rowName, rowCategory)
                              : genericImageUrl
                          )
                        }
                      />
                      {row.amount === lowestAmount && <small className="deal-badge blue">Low</small>}
                      <button
                        className={isSaved ? "heart-button saved" : "heart-button"}
                        type="button"
                        aria-label={isSaved ? `${rowName} saved` : `Save ${rowName} to watchlist`}
                        onClick={() =>
                          saveProduct(rowName, rowCategory, `Seen at ${row.store} for ${formatCurrency(row.amount)}`)
                        }
                        disabled={isSaved}
                      >
                        <Heart size={16} fill={isSaved ? "currentColor" : "none"} />
                      </button>
                    </div>

                    <div className="product-card-body">
                      <div className="product-category">{rowCategory}</div>
                      <h2>{rowName}</h2>

                      <div className="price-block">
                        <div className="price-line">
                          <strong>{formatCurrency(row.amount)}</strong>
                        </div>
                        <p>
                          <i className={row.estimated ? "status-dot yellow" : "status-dot green"} />
                          {row.estimated
                            ? "Est. price at "
                            : row.amount === lowestAmount
                              ? "Lowest live at "
                              : "Live price at "}
                          <b>{row.store}</b>
                        </p>

                        <StoreStrip row={row} />
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          </>
        )}
      </div>
    </section>
  );
}

function StoreStrip({ row }: { row: PriceOffer }) {
  const stores = row.storeOffers?.length
    ? row.storeOffers
    : [{ store: row.store, amount: row.amount, logoUrl: row.logoUrl }];
  const cheapest = stores[0];
  const priciest = stores[stores.length - 1];

  return (
    <div className="store-strip">
      <div className="store-icons">
        {stores.slice(0, 4).map((storeOffer) => (
          <span
            key={storeOffer.store}
            className="store-avatar"
            title={`${storeOffer.store}: ${formatCurrency(storeOffer.amount)}`}
          >
            {storeOffer.logoUrl ? <img src={storeOffer.logoUrl} alt="" /> : storeOffer.store.charAt(0)}
          </span>
        ))}
      </div>
      <span>
        Found at {stores.length} store{stores.length === 1 ? "" : "s"}
      </span>
      <strong>
        {stores.length === 1
          ? formatCurrency(cheapest.amount)
          : `${formatCurrency(cheapest.amount)} - ${formatCurrency(priciest.amount)}`}
      </strong>
    </div>
  );
}

function fullProductName(name: string | undefined, description: string | undefined, fallback: string) {
  const cleanName = cleanProductText(name);
  const cleanDescription = cleanProductText(description);
  const cleanFallback = cleanProductText(fallback);

  if (
    cleanDescription &&
    cleanDescription.length > cleanFallback.length &&
    !isGenericProductDescription(cleanDescription)
  ) {
    return cleanDescription;
  }

  return cleanName || cleanFallback;
}

function cleanProductText(value: string | undefined) {
  return (value ?? "").replace(/\s+/g, " ").trim();
}

function replaceBrokenImage(event: SyntheticEvent<HTMLImageElement>, fallbackImageUrl: string) {
  if (event.currentTarget.src !== fallbackImageUrl) {
    event.currentTarget.onerror = null;
    event.currentTarget.src = fallbackImageUrl;
  }
}

function fallbackProductImageUrl(productName: string, category: string) {
  const label = cleanProductText(productName) || "Product";
  const categoryLabel = cleanProductText(category).toLowerCase();
  const shortLabel = label.length > 24 ? `${label.slice(0, 21)}...` : label;
  const safeLabel = escapeSvgText(shortLabel);
  const safeCategory = escapeSvgText(categoryLabel);
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" role="img" aria-label="${safeLabel}">
      <rect width="240" height="240" rx="18" fill="#f8fafc"/>
      <rect x="48" y="40" width="144" height="132" rx="14" fill="#ffffff" stroke="#cbd5e1" stroke-width="4"/>
      <path d="M76 82h88M76 112h88M76 142h56" stroke="#94a3b8" stroke-width="10" stroke-linecap="round"/>
      <circle cx="178" cy="58" r="24" fill="#16a34a"/>
      <text x="120" y="198" text-anchor="middle" font-family="Arial, sans-serif" font-size="18" font-weight="700" fill="#0f172a">${safeLabel}</text>
      <text x="120" y="220" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="700" fill="#64748b">${safeCategory}</text>
    </svg>
  `;

  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function isGenericProductDescription(value: string) {
  const lower = value.toLowerCase();
  return [
    "product details could not be read",
    "shop securely online",
    "locate a makro",
    "best deals groceries",
    "online shopping",
    "same in-store prices",
    "delivery in as little",
    "fast & reliable delivery",
  ].some((phrase) => lower.includes(phrase));
}
