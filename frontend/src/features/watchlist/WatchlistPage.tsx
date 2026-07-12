import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookmarkPlus, Search, Trash2 } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/ui/EmptyState";
import { categories } from "../prices/priceTypes";
import { useAuth } from "../auth/AuthProvider";
import { addWatchlistItem, getWatchlist, removeWatchlistItem } from "./watchlistApi";

export function WatchlistPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const userEmail = user?.email ?? "";
  const [productName, setProductName] = useState("");
  const [category, setCategory] = useState("GROCERY");
  const [note, setNote] = useState("");

  const query = useQuery({
    queryKey: ["watchlist", userEmail],
    queryFn: () => getWatchlist(userEmail),
    enabled: userEmail.length > 0,
  });

  const addMutation = useMutation({
    mutationFn: addWatchlistItem,
    onSuccess: () => {
      setProductName("");
      setNote("");
      queryClient.invalidateQueries({ queryKey: ["watchlist", userEmail] });
    },
  });

  const removeMutation = useMutation({
    mutationFn: (id: number) => removeWatchlistItem(id, userEmail),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["watchlist", userEmail] });
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = productName.trim();
    if (!trimmed) {
      return;
    }

    addMutation.mutate({ userEmail, productName: trimmed, category, note: note.trim() || undefined });
  }

  const items = query.data ?? [];

  return (
    <section className="content-section">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Private</p>
          <h1>Watchlist</h1>
          <p>Products you are tracking, saved to your account.</p>
        </div>
        <Link to="/" className="primary-button inline-button">
          <Search size={17} />
          Search product
        </Link>
      </div>

      <form className="stacked-form" onSubmit={handleSubmit}>
        <label htmlFor="watchlist-product">Product</label>
        <input
          id="watchlist-product"
          value={productName}
          onChange={(event) => setProductName(event.target.value)}
          placeholder="e.g. Milk 2L"
          required
        />

        <label htmlFor="watchlist-category">Category</label>
        <select
          id="watchlist-category"
          value={category}
          onChange={(event) => setCategory(event.target.value)}
        >
          {categories.map((option) => (
            <option key={option} value={option}>
              {option.toLowerCase()}
            </option>
          ))}
        </select>

        <label htmlFor="watchlist-note">Note (optional)</label>
        <input
          id="watchlist-note"
          value={note}
          onChange={(event) => setNote(event.target.value)}
          placeholder="e.g. Buy below R180"
        />

        <button type="submit" className="primary-button" disabled={addMutation.isPending}>
          <BookmarkPlus size={17} />
          {addMutation.isPending ? "Saving..." : "Add to watchlist"}
        </button>
      </form>

      {query.isLoading ? <p>Loading watchlist...</p> : null}
      {query.isError ? (
        <EmptyState
          title="Could not load your watchlist"
          description="Make sure the backend is running on port 8081, then try again."
        />
      ) : null}

      {query.isSuccess && items.length === 0 ? (
        <EmptyState
          title="Nothing saved yet"
          description="Add a product above or save one from the search results to start tracking it."
        />
      ) : null}

      <div className="card-grid">
        {items.map((item) => (
          <article className="data-card" key={item.id}>
            <span>{item.category.toLowerCase()}</span>
            <h2>{item.productName}</h2>
            <p>{item.note || "No note"}</p>
            <button
              type="button"
              className="icon-button"
              aria-label={`Remove ${item.productName} from watchlist`}
              onClick={() => removeMutation.mutate(item.id)}
              disabled={removeMutation.isPending}
            >
              <Trash2 size={16} />
            </button>
          </article>
        ))}
      </div>
    </section>
  );
}
