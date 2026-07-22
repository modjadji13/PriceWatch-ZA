import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Heart, LayoutGrid, List, Search } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { addWatchlistItem } from "../watchlist/watchlistApi";
import { getDeals } from "./dealsApi";
import type { Deal } from "./dealsApi";
import { formatCurrency } from "../../lib/formatCurrency";
import { categories } from "./priceTypes";

type SaleProduct = {
  id: number;
  category: string;
  title: string;
  price: string;
  oldPrice: string;
  store: string;
  stores: number;
  range: string;
  badge: string;
  badgeTone: string;
  storesCompared: string[];
  estimated: boolean;
  imageShape: string;
  imageUrl: string;
};

function storeLogo(store: string) {
  const domains: Record<string, string> = {
    Checkers: "checkers.co.za",
    Shoprite: "shoprite.co.za",
    "Pick n Pay": "pnp.co.za",
    Woolworths: "woolworths.co.za",
    Takealot: "takealot.com",
    Makro: "makro.co.za",
    Spar: "spar.co.za",
    Clicks: "clicks.co.za",
    "Dis-Chem": "dischem.co.za",
    Loot: "loot.co.za",
    "HiFi Corp": "hificorp.co.za",
  };
  const domain = domains[store];
  return domain ? `https://www.google.com/s2/favicons?domain=${domain}&sz=64` : "";
}

function parseRandAmount(price: string) {
  return Number(price.replace(/[^\d.]/g, "")) || 0;
}

function dealToSaleProduct(deal: Deal): SaleProduct {
  return {
    id: deal.productId,
    category: deal.category ? deal.category[0] + deal.category.slice(1).toLowerCase() : "",
    title: deal.title,
    price: formatCurrency(deal.currentPrice),
    oldPrice: formatCurrency(deal.oldPrice),
    store: deal.store,
    stores: deal.storeCount,
    range: `${formatCurrency(deal.rangeLow)} - ${formatCurrency(deal.rangeHigh)}`,
    badge: `-${deal.discountPercent}%`,
    badgeTone: "red",
    storesCompared: deal.storesCompared,
    estimated: deal.estimated,
    imageShape: "square",
    imageUrl: deal.imageUrl,
  };
}

export function SearchPage() {
  const [searchParams] = useSearchParams();
  const requestedCategory = searchParams.get("category")?.toUpperCase();
  const selectedCategory = categories.find((item) => item === requestedCategory) ?? "GROCERY";
  const [product, setProduct] = useState("");
  const [category, setCategory] = useState<string>(selectedCategory);
  const [sort, setSort] = useState<"relevance" | "price">("relevance");
  const [view, setView] = useState<"grid" | "list">("grid");
  const [savedTitles, setSavedTitles] = useState<Set<string>>(new Set());
  const searchInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const { user } = useAuth();

  useEffect(() => {
    setCategory(selectedCategory);
  }, [selectedCategory]);

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

  const dealsQuery = useQuery({
    queryKey: ["deals"],
    queryFn: getDeals,
  });

  const products = useMemo(() => {
    // Deals arrive already ordered by biggest discount, which is the "relevance"
    // ordering; only the price sort needs to reorder.
    const mapped = (dealsQuery.data ?? []).map(dealToSaleProduct);
    if (sort === "price") {
      return [...mapped].sort((left, right) => parseRandAmount(left.price) - parseRandAmount(right.price));
    }

    return mapped;
  }, [dealsQuery.data, sort]);

  const saveMutation = useMutation({
    mutationFn: addWatchlistItem,
    onSuccess: (item) => {
      setSavedTitles((previous) => new Set(previous).add(item.productName));
    },
  });

  function saveProduct(item: SaleProduct) {
    if (!user) {
      navigate("/login", { state: { from: "/" } });
      return;
    }

    saveMutation.mutate({
      userEmail: user.email,
      productName: item.title,
      category: "GROCERY",
      note: `On sale at ${item.store} for ${item.price}`,
    });
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = product.trim();
    if (!trimmed) {
      return;
    }

    navigate(`/results?product=${encodeURIComponent(trimmed)}&category=${encodeURIComponent(category)}`);
  }

  return (
    <section className="dashboard-page">
      <div className="search-toolbar">
        <div className="toolbar-row">
          <form className="toolbar-search" onSubmit={handleSubmit}>
            <Search size={16} />
            <input
              id="product"
              ref={searchInputRef}
              value={product}
              onChange={(event) => setProduct(event.target.value)}
              placeholder="Search products across all stores..."
            />
            <kbd>Ctrl K</kbd>
          </form>

          <div className="category-select">
            <select value={category} onChange={(event) => setCategory(event.target.value)} aria-label="Category">
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
              className={sort === "relevance" ? "active" : undefined}
              onClick={() => setSort("relevance")}
            >
              Relevance
            </button>
            <button
              type="button"
              className={sort === "price" ? "active" : undefined}
              onClick={() => setSort("price")}
            >
              Lowest Price
            </button>
          </div>
        </div>

        <div className="result-count-row">
          <span>
            {products.length > 0 ? (
              <>
                Showing <strong>1-{products.length}</strong> of <strong>{products.length}</strong> current sale items
              </>
            ) : (
              "Current sale items"
            )}
          </span>
          <div>
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
        {dealsQuery.isLoading && (
          <div className="loading-block" role="status" aria-live="polite">
            <span className="spinner" aria-hidden="true" />
            <span>Finding today's real price drops...</span>
          </div>
        )}

        {dealsQuery.isError && (
          <div className="error-block">
            Deals are unavailable right now. Start the Spring backend on port 8081 and try again.
          </div>
        )}

        {!dealsQuery.isLoading && !dealsQuery.isError && products.length === 0 && (
          <div className="empty-state">
            <h2>No sale items yet</h2>
            <p>
              We only show genuine price drops measured from tracked history. As more prices are
              recorded, real deals will appear here. Search a product above to compare prices now.
            </p>
          </div>
        )}

        {products.length > 0 && (
          <div className={view === "list" ? "product-grid list-view" : "product-grid"}>
            {products.map((item) => (
              <ProductCard
                key={item.id}
                product={item}
                saved={savedTitles.has(item.title)}
                onSave={() => saveProduct(item)}
              />
            ))}
          </div>
        )}

        <DashboardFooter />
      </div>
    </section>
  );
}

function ProductCard({
  product,
  saved,
  onSave,
}: {
  product: SaleProduct;
  saved: boolean;
  onSave: () => void;
}) {
  return (
    <article className="product-card">
      <div className="product-art">
        {product.imageUrl ? (
          <img className="sale-product-image" src={product.imageUrl} alt={product.title} />
        ) : (
          <div className={`image-placeholder ${product.imageShape}`}>
            <span>Image</span>
          </div>
        )}
        {product.badge && <small className={`deal-badge ${product.badgeTone}`}>{product.badge}</small>}
        <button
          className={saved ? "heart-button saved" : "heart-button"}
          type="button"
          aria-label={saved ? `${product.title} saved` : `Save ${product.title} to watchlist`}
          onClick={onSave}
          disabled={saved}
        >
          <Heart size={16} fill={saved ? "currentColor" : "none"} />
        </button>
      </div>

      <div className="product-card-body">
        <div className="product-category">{product.category}</div>
        <h2>{product.title}</h2>

        <div className="price-block">
          <div className="price-line">
            <strong>{product.price}</strong>
            {product.oldPrice && <span>{product.oldPrice}</span>}
          </div>
          <p>
            <i className={product.estimated ? "status-dot yellow" : "status-dot green"} />
            {product.estimated ? "Est. price at " : "Live price at "}
            <b>{product.store}</b>
          </p>

          <div className="store-strip">
            <div className="store-icons">
              {product.storesCompared.map((store) => (
                <span key={store} className="store-avatar" title={store}>
                  <img src={storeLogo(store)} alt="" />
                </span>
              ))}
            </div>
            <span>{product.stores} stores</span>
            <strong>{product.range}</strong>
          </div>
        </div>
      </div>
    </article>
  );
}

function DashboardFooter() {
  return (
    <footer className="dashboard-footer">
      <div>
        <h4>PriceWatchZA</h4>
        <p>Transparent price tracking and comparison for South African consumers.</p>
      </div>
      <div>
        <h4>Categories</h4>
        <Link to="/?category=GROCERY">Groceries</Link>
        <Link to="/?category=ELECTRONICS">Electronics</Link>
        <Link to="/?category=HOUSEHOLD">Household</Link>
      </div>
      <div>
        <h4>Monitored Stores</h4>
        <a href="https://www.checkers.co.za" target="_blank" rel="noreferrer">
          Checkers
        </a>
        <a href="https://www.pnp.co.za" target="_blank" rel="noreferrer">
          Pick n Pay
        </a>
        <a href="https://www.takealot.com" target="_blank" rel="noreferrer">
          Takealot
        </a>
      </div>
      <div>
        <h4>Platform</h4>
        <Link to="/app/watchlist">Watchlist</Link>
        <Link to="/app/alerts">Price alerts</Link>
        <Link to="/app/profile">Your profile</Link>
      </div>
    </footer>
  );
}
