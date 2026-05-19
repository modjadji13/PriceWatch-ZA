import { FormEvent, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Bell, BookmarkPlus, Heart, LayoutGrid, List, RefreshCw, Search } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { formatCurrency } from "../../lib/formatCurrency";
import { useAuth } from "../auth/AuthProvider";
import { comparePrices } from "./priceApi";
import { categories } from "./priceTypes";

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
      .sort((a, b) => a.amount - b.amount);
  }, [query.data]);

  const bestPrice = rows[0];
  const details = query.data?.details;
  const displayProductName = fullProductName(details?.name, details?.description, product);

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
                <article className="product-card" key={row.store}>
                  <div className="product-art">
                    {details?.imageUrl ? (
                      <img className="result-product-image" src={details.imageUrl} alt={displayProductName} />
                    ) : (
                      <div className="image-placeholder tall">
                        <span>Image</span>
                      </div>
                    )}
                    {index === 0 && <small className="deal-badge blue">Low</small>}
                    <button className="heart-button" type="button" aria-label="Save product">
                      <Heart size={16} />
                    </button>
                  </div>

                  <div className="product-card-body">
                    <div className="product-category">{category.toLowerCase()}</div>
                    <h2>{displayProductName}</h2>

                    <div className="price-block">
                      <div className="price-line">
                        <strong>{formatCurrency(row.amount)}</strong>
                      </div>
                      <p>
                        <i className={row.estimated ? "status-dot yellow" : "status-dot green"} />
                        {row.estimated ? "Est. price at " : index === 0 ? "Lowest live at " : "Live price at "}
                        <b>{row.store}</b>
                      </p>

                      <div className="store-strip">
                        <div className="store-icons">
                          {rows.slice(0, 3).map((storeRow) => (
                            <span key={storeRow.store} className="store-avatar" title={storeRow.store}>
                              {storeRow.logoUrl ? <img src={storeRow.logoUrl} alt="" /> : storeRow.store.charAt(0)}
                            </span>
                          ))}
                        </div>
                        <span>{rows.length} stores</span>
                        <strong>
                          {formatCurrency(bestPrice.amount)} - {formatCurrency(rows[rows.length - 1].amount)}
                        </strong>
                      </div>
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
