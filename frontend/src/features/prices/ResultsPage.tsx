import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Bell, BookmarkPlus, RefreshCw } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { formatCurrency } from "../../lib/formatCurrency";
import { useAuth } from "../auth/AuthProvider";
import { comparePrices } from "./priceApi";

export function ResultsPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const product = searchParams.get("product") ?? "";
  const category = searchParams.get("category") ?? "GROCERY";

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

  function requireLogin(path: string) {
    if (user) {
      navigate(path);
      return;
    }

    navigate("/login", { state: { from: `/results?product=${product}&category=${category}` } });
  }

  if (!product) {
    return (
      <section className="content-section">
        <h1>Search a product first</h1>
        <p>Start from the search page to compare prices across stores.</p>
        <Link to="/" className="primary-button inline-button">
          Search prices
        </Link>
      </section>
    );
  }

  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{category.toLowerCase()}</p>
          <h1>{product}</h1>
          <p>Prices, product image, and product details from the backend scraper.</p>
        </div>
        <button type="button" className="secondary-button" onClick={() => query.refetch()}>
          <RefreshCw size={17} />
          Refresh
        </button>
      </div>

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
          <div className="product-result-card">
            <div className="product-image-frame">
              {details?.imageUrl ? (
                <img src={details.imageUrl} alt={details.name || product} />
              ) : (
                <span>No image found</span>
              )}
            </div>
            <div>
              <span>Product details</span>
              <h2>{details?.name || product}</h2>
              <p>{details?.description || "No product details were readable from the store page."}</p>
              {details?.sourceStore && <small>Details source: {details.sourceStore}</small>}
            </div>
          </div>

          <div className="result-summary">
            <article>
              <span>Best price</span>
              <strong>{formatCurrency(bestPrice.amount)}</strong>
              <small>{bestPrice.store}</small>
            </article>
            <article>
              <span>Stores checked</span>
              <strong>{rows.length}</strong>
              <small>Sorted from lowest to highest</small>
            </article>
          </div>

          <div className="results-table-wrap">
            <table className="results-table">
              <thead>
                <tr>
                  <th>Store</th>
                  <th>Price</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, index) => (
                  <tr key={row.store}>
                    <td>
                      <span className="store-cell">
                        {row.logoUrl ? <img src={row.logoUrl} alt="" /> : <span className="store-logo-fallback" />}
                        <span>{row.store}</span>
                      </span>
                    </td>
                    <td>{formatCurrency(row.amount)}</td>
                    <td>{row.estimated ? "Estimate" : index === 0 ? "Lowest live" : "Live"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

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
        </>
      )}
    </section>
  );
}
