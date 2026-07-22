import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { Heart, LayoutGrid, List, Search } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { addWatchlistItem } from "../watchlist/watchlistApi";
import { CategoryDropdown } from "./CategoryDropdown";
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

const FEATURED_PRODUCTS: SaleProduct[] = [
  {
    id: 1,
    category: "Pantry",
    title: "Selati White Sugar 500g",
    price: "R14.99",
    oldPrice: "R18.99",
    store: "Checkers",
    stores: 4,
    range: "R14 - R22",
    badge: "-21%",
    badgeTone: "red",
    storesCompared: ["Checkers", "Pick n Pay", "Makro"],
    estimated: false,
    imageShape: "square",
    imageUrl: "https://img.mrdfood.com/fit-in/filters:format(jpeg):fill(white):background_color(ffffff)/480x480/groceries/product/6b987fe1-d2b0-402b-aa14-f59e4571fe3b.png",
  },
  {
    id: 2,
    category: "Dairy",
    title: "Clover Fresh Full Cream Milk 2L",
    price: "R31.99",
    oldPrice: "R39.99",
    store: "Pick n Pay",
    stores: 5,
    range: "R31 - R45",
    badge: "-20%",
    badgeTone: "red",
    storesCompared: ["Pick n Pay", "Checkers", "Makro"],
    estimated: false,
    imageShape: "tall",
    imageUrl: "https://www.clover.co.za/wp-content/uploads/2018/05/Fresh-fullcream-2l-2024_featured.png",
  },
  {
    id: 3,
    category: "Household",
    title: "Sunlight Dishwashing Liquid Lemon 750ml",
    price: "R24.99",
    oldPrice: "R34.99",
    store: "Takealot",
    stores: 3,
    range: "R24 - R38",
    badge: "-29%",
    badgeTone: "red",
    storesCompared: ["Takealot", "Makro", "Checkers"],
    estimated: false,
    imageShape: "tall",
    imageUrl: "https://originsworldfoods.com/cdn/shop/products/112762_1200x1200.jpg?v=1636964920",
  },
  {
    id: 4,
    category: "Groceries",
    title: "Tastic Parboiled Rice 2kg",
    price: "R34.99",
    oldPrice: "R42.99",
    store: "Makro",
    stores: 5,
    range: "R34 - R49",
    badge: "-19%",
    badgeTone: "red",
    storesCompared: ["Makro", "Checkers", "Pick n Pay"],
    estimated: false,
    imageShape: "tall",
    imageUrl: "https://welkomusa.com/cdn/shop/files/tastic_2kg_1200x1504.png?v=1771870864",
  },
];

const STORE_LOGOS: Record<string, string> = {
  Checkers: "https://www.google.com/s2/favicons?domain=checkers.co.za&sz=64",
  "Pick n Pay": "https://www.google.com/s2/favicons?domain=pnp.co.za&sz=64",
  Takealot: "https://www.google.com/s2/favicons?domain=takealot.com&sz=64",
  Makro: "https://www.google.com/s2/favicons?domain=makro.co.za&sz=64",
  Woolworths: "https://www.google.com/s2/favicons?domain=woolworths.co.za&sz=64",
};

function parseRandAmount(price: string) {
  return Number(price.replace(/[^\d.]/g, "")) || 0;
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

  const products = useMemo(() => {
    if (sort === "price") {
      return [...FEATURED_PRODUCTS].sort((a, b) => parseRandAmount(a.price) - parseRandAmount(b.price));
    }

    return FEATURED_PRODUCTS;
  }, [sort]);

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

          <CategoryDropdown value={category} onChange={setCategory} />

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
            Showing <strong>1-{products.length}</strong> of <strong>{products.length}</strong> current sale items
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
                  <img src={STORE_LOGOS[store]} alt="" />
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
