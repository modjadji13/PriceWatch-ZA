import { useMemo, useState } from "react";
import type { FormEvent, SyntheticEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Bell, BookmarkPlus, Heart, LayoutGrid, List, RefreshCw, Search } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { formatCurrency } from "../../lib/formatCurrency";
import { escapeSvgText } from "../../lib/svg";
import { useAuth } from "../auth/AuthProvider";
import { comparePrices } from "./priceApi";
import { categories } from "./priceTypes";
import type { PriceOffer } from "./priceTypes";
import { productFallbackFor } from "./productFallbacks";

export function ResultsPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const product = searchParams.get("product") ?? "";
  const category = searchParams.get("category") ?? "GROCERY";
  const [searchProduct, setSearchProduct] = useState(product);
  const [searchCategory, setSearchCategory] = useState(category);

  const query = useQuery({
    queryKey: ["price-compare", product, category],
    queryFn: () => comparePrices(product, category),
    enabled: product.trim().length > 0,
  });

  const rows = useMemo(() => {
    return (query.data?.prices ?? [])
      .filter((item) => !item.estimated)
      .sort((a, b) => a.amount - b.amount);
  }, [query.data]);

  const details = query.data?.details;
  const productFallback = productFallbackFor(product);
  const displayProductName = productFallback?.name ?? fullProductName(details?.name, details?.description, product);
  const displayCategory = productFallback?.category ?? category;
  const genericImageUrl = fallbackProductImageUrl(displayProductName, displayCategory);
  const productImageUrl = details?.imageUrl || productFallback?.imageUrl || genericImageUrl;

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = searchProduct.trim();
    if (!trimmed) {
      return;
    }

    navigate(`/results?product=${encodeURIComponent(trimmed)}&category=${encodeURIComponent(searchCategory)}`);
  }

  function requireLogin(path: string) {
    if (user) {
      navigate(path);
      return;
    }

    navigate("/login", { state: { from: `/results?product=${product}&category=${category}` } });
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
            <button type="button" className="active">Relevance</button>
            <button type="button">Lowest Price</button>
            <button type="button">Biggest Drop</button>
            <button type="button">Rating</button>
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
            <button type="button" aria-label="Grid view">
              <LayoutGrid size={16} />
            </button>
            <button type="button" aria-label="List view">
              <List size={16} />
            </button>
          </div>
        </div>
      </div>

      <div className="product-grid-shell">
        {query.isLoading && <div className="loading-block">Checking store prices...</div>}

        {query.isError && (
          <div className="error-block">
            The price service is not reachable yet. Start the Spring backend on port 8080 and try again.
          </div>
        )}

        {!query.isLoading && !query.isError && rows.length === 0 && (
          <div className="empty-state">
            <h2>No prices found</h2>
            <p>Try a different product name or category.</p>
          </div>
        )}

        {rows.length > 0 && (
          <>
          <div className="action-strip">
            <button type="button" className="secondary-button" onClick={() => requireLogin("/app/watchlist")}>
              <BookmarkPlus size={17} />
              Save to watchlist
            </button>
            <button type="button" className="secondary-button" onClick={() => requireLogin("/app/alerts")}>
              <Bell size={17} />
              Create alert
            </button>
          </div>

            <div className="product-grid">
              {rows.map((row, index) => (
                <article className="product-card" key={`${row.productName || row.store}-${index}`}>
                  <div className="product-art">
                    <img
                      className="result-product-image"
                      src={row.productImageUrl || productImageUrl}
                      alt={row.productName || displayProductName}
                      onError={(event) =>
                        replaceBrokenImage(
                          event,
                          row.productImageUrl ? fallbackProductImageUrl(row.productName || displayProductName, row.productCategory || displayCategory) : genericImageUrl
                        )
                      }
                    />
                    {index === 0 && <small className="deal-badge blue">Low</small>}
                    <button className="heart-button" type="button" aria-label="Save product">
                      <Heart size={16} />
                    </button>
                  </div>

                  <div className="product-card-body">
                    <div className="product-category">{row.productCategory || displayCategory}</div>
                    <h2>{row.productName || displayProductName}</h2>

                    <div className="price-block">
                      <div className="price-line">
                        <strong>{formatCurrency(row.amount)}</strong>
                      </div>
                      <p>
                        <i className={row.estimated ? "status-dot yellow" : "status-dot green"} />
                        {index === 0 ? "Lowest live at " : "Live price at "}
                        <b>{row.store}</b>
                      </p>

                      <StoreStrip row={row} />
                    </div>
                  </div>

                </article>
              ))}
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
