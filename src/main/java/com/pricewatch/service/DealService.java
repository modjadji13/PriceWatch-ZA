package com.pricewatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewatch.dto.DealDto;
import com.pricewatch.model.SearchResult;
import com.pricewatch.repository.SearchResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the home page's "current sale items" from sales the stores are
 * advertising right now. Every cached store comparison carries, per offer, the
 * store's own "was" price ({@code originalAmount}); an offer priced below its
 * was-price is a live markdown. Those markdowns are pooled across every cached
 * search, de-duplicated per item, and returned biggest discount first. The
 * title, image, price and discount all come from the one offer the store is
 * running the sale on, so a card can never mismatch a product with the wrong
 * picture or an invented discount.
 */
@Service
public class DealService {
    private static final Logger logger = LoggerFactory.getLogger(DealService.class);

    // Ignore trivial roundings as "sales", and reject implausibly large drops
    // that almost always mean a bad was-price in the store's own data.
    private static final double MIN_DISCOUNT = 0.05;
    private static final double MAX_DISCOUNT = 0.95;
    private static final int MAX_DEALS = 12;
    private static final int MAX_STORE_AVATARS = 3;

    private final SearchResultRepository searchResultRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DealService(SearchResultRepository searchResultRepository) {
        this.searchResultRepository = searchResultRepository;
    }

    public List<DealDto> currentDeals() {
        // Keyed by item so the same product surfacing under several search terms
        // (e.g. "kettle" and "electric kettle") yields one card, keeping the
        // biggest advertised discount seen for it.
        Map<String, DealDto> byItem = new LinkedHashMap<>();

        for (SearchResult saved : searchResultRepository.findAll()) {
            JsonNode root;
            try {
                root = objectMapper.readTree(saved.getResultJson());
            } catch (Exception e) {
                logger.debug("Skipping unreadable cached result '{}': {}", saved.getSearchTerm(), e.getMessage());
                continue;
            }

            String resultCategory = root.path("category").asText(saved.getCategory());
            for (JsonNode offer : root.path("prices")) {
                DealDto deal = toDeal(offer, resultCategory);
                if (deal == null) {
                    continue;
                }
                String key = dedupeKey(offer, deal);
                DealDto existing = byItem.get(key);
                if (existing == null || deal.discountPercent() > existing.discountPercent()) {
                    byItem.put(key, deal);
                }
            }
        }

        List<DealDto> deals = new ArrayList<>(byItem.values());
        deals.sort(Comparator.comparingInt(DealDto::discountPercent).reversed());
        return deals.size() > MAX_DEALS ? deals.subList(0, MAX_DEALS) : deals;
    }

    private DealDto toDeal(JsonNode offer, String resultCategory) {
        if (offer.path("estimated").asBoolean(false)) {
            return null;
        }
        double amount = offer.path("amount").asDouble(0.0);
        double originalAmount = offer.path("originalAmount").asDouble(0.0);
        if (amount <= 0 || originalAmount <= amount) {
            return null;
        }

        double discount = (originalAmount - amount) / originalAmount;
        if (discount < MIN_DISCOUNT || discount > MAX_DISCOUNT) {
            return null;
        }

        String store = offer.path("store").asText("");
        String title = offer.path("productName").asText("");
        if (title.isBlank()) {
            return null;
        }
        String category = firstNotBlank(offer.path("productCategory").asText(""), resultCategory);
        String imageUrl = offer.path("productImageUrl").asText("");

        // The offer already carries every store selling this item, so store count
        // and price range describe exactly this product across stores.
        List<Double> storePrices = new ArrayList<>();
        List<String> storesCompared = new ArrayList<>();
        for (JsonNode storeOffer : offer.path("storeOffers")) {
            double storeAmount = storeOffer.path("amount").asDouble(0.0);
            if (storeAmount > 0) {
                storePrices.add(storeAmount);
            }
            if (storesCompared.size() < MAX_STORE_AVATARS) {
                String name = storeOffer.path("store").asText("");
                if (!name.isBlank()) {
                    storesCompared.add(name);
                }
            }
        }
        if (storePrices.isEmpty()) {
            storePrices.add(amount);
        }
        if (storesCompared.isEmpty() && !store.isBlank()) {
            storesCompared.add(store);
        }
        double rangeLow = storePrices.stream().min(Double::compareTo).orElse(amount);
        double rangeHigh = storePrices.stream().max(Double::compareTo).orElse(amount);
        int storeCount = Math.max(storePrices.size(), 1);

        return new DealDto(
            stableId(offer, title, store),
            title,
            category,
            round2(amount),
            round2(originalAmount),
            (int) Math.round(discount * 100),
            store,
            storeCount,
            round2(rangeLow),
            round2(rangeHigh),
            imageUrl,
            storesCompared,
            false);
    }

    // Same item across different search terms: prefer the store's product URL,
    // else fall back to store + product name.
    private String dedupeKey(JsonNode offer, DealDto deal) {
        String url = offer.path("productUrl").asText("");
        if (!url.isBlank()) {
            return "u:" + url.trim().toLowerCase();
        }
        return "n:" + deal.store().toLowerCase() + "|" + deal.title().toLowerCase();
    }

    // The frontend needs a numeric, stable-per-item id for its list key; the
    // cached offers carry no product row id, so derive one from the dedupe key.
    private long stableId(JsonNode offer, String title, String store) {
        String url = offer.path("productUrl").asText("");
        String basis = url.isBlank() ? store + "|" + title : url;
        return Integer.toUnsignedLong(basis.toLowerCase().hashCode());
    }

    private static String firstNotBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
