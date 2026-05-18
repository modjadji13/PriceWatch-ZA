import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { formatCurrency } from "../../lib/formatCurrency";
import { getHighestPrice, getLowestPrice, getPriceHistory } from "./priceApi";

export function ProductDetailPage() {
  const { productId = "" } = useParams();

  const history = useQuery({
    queryKey: ["price-history", productId],
    queryFn: () => getPriceHistory(productId),
    enabled: productId.length > 0,
  });

  const lowest = useQuery({
    queryKey: ["price-lowest", productId],
    queryFn: () => getLowestPrice(productId),
    enabled: productId.length > 0,
  });

  const highest = useQuery({
    queryKey: ["price-highest", productId],
    queryFn: () => getHighestPrice(productId),
    enabled: productId.length > 0,
  });

  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <p className="eyebrow">product #{productId}</p>
          <h1>Price history</h1>
          <p>Historical price records for an existing backend product ID.</p>
        </div>
        <Link to="/" className="secondary-button">
          New search
        </Link>
      </div>

      <div className="result-summary">
        <article>
          <span>Lowest recorded</span>
          <strong>{lowest.data ? formatCurrency(lowest.data.amount) : "--"}</strong>
          <small>{lowest.data?.store ?? "No record"}</small>
        </article>
        <article>
          <span>Highest recorded</span>
          <strong>{highest.data ? formatCurrency(highest.data.amount) : "--"}</strong>
          <small>{highest.data?.store ?? "No record"}</small>
        </article>
      </div>

      {history.isLoading && <div className="loading-block">Loading price history...</div>}
      {history.isError && <div className="error-block">Could not load history for this product.</div>}

      {history.data && (
        <div className="results-table-wrap">
          <table className="results-table">
            <thead>
              <tr>
                <th>Store</th>
                <th>Price</th>
                <th>Recorded</th>
              </tr>
            </thead>
            <tbody>
              {history.data.map((row) => (
                <tr key={row.id}>
                  <td>{row.store}</td>
                  <td>{formatCurrency(row.amount)}</td>
                  <td>{new Date(row.recordedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
