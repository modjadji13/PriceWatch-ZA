package com.pricewatch.service;

import com.pricewatch.dto.DealDto;
import com.pricewatch.repository.PriceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the home page's "current sale items" from real recorded price history:
 * a specific item whose cheapest recent price at some store sits meaningfully
 * below that same store's cheapest price for the same item over the prior month.
 * The title, image and discount all come from that one item's history, so a
 * card can never pair one product's price with another product's picture.
 */
@Service
public class DealService {

    // A recent minimum must be at least this far below the prior minimum to count
    // as a deal, so ordinary noise does not surface as a fake sale. Because each
    // price row now carries the exact item it was scraped for, a drop compares one
    // item against its own past rather than whatever cheapest thing matched the
    // search term, so no upper "artifact" cap is needed; MAX_DISCOUNT only rejects
    // near-certainly-erroneous scrapes (e.g. a stray R1 price), not real promos.
    private static final double MIN_DISCOUNT = 0.10;
    private static final double MAX_DISCOUNT = 0.90;
    private static final int RECENT_DAYS = 3;
    private static final int BASELINE_DAYS = 30;
    private static final int MAX_DEALS = 8;
    private static final int MAX_STORE_AVATARS = 3;

    private final PriceRepository priceRepository;

    public DealService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    public List<DealDto> currentDeals() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime recentSince = now.minusDays(RECENT_DAYS);
        LocalDateTime baselineSince = now.minusDays(BASELINE_DAYS);

        List<Object[]> rows = priceRepository.findDealCandidates(recentSince, baselineSince);

        // Each row is one (store, item) pair. Cluster rows into one entry per
        // physical item so that store count and price range describe that single
        // item across stores rather than everything that matched the search term.
        // Different stores link the same item under different product URLs, so a
        // normalized item name is the only key that unites them.
        Map<String, ItemCluster> byItem = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long productId = toLong(row[0]);
            String itemName = asString(row[4]);
            String key = clusterKey(productId, itemName);
            if (key == null) {
                continue;
            }
            ItemCluster cluster = byItem.computeIfAbsent(
                key, k -> new ItemCluster(productId, asString(row[2])));
            cluster.stores.add(new StorePrices(
                asString(row[3]),
                toDouble(row[6]),
                toDouble(row[7]),
                itemName,
                asString(row[5])));
        }

        List<DealDto> deals = new ArrayList<>();
        for (ItemCluster cluster : byItem.values()) {
            DealDto deal = toDeal(cluster);
            if (deal != null) {
                deals.add(deal);
            }
        }

        deals.sort(Comparator.comparingInt(DealDto::discountPercent).reversed());
        return deals.size() > MAX_DEALS ? deals.subList(0, MAX_DEALS) : deals;
    }

    private DealDto toDeal(ItemCluster cluster) {
        // Current in-stock stores define the range and store count shown on the card.
        List<StorePrices> current = cluster.stores.stream()
            .filter(store -> store.currentMin != null && store.currentMin > 0)
            .sorted(Comparator.comparingDouble(store -> store.currentMin))
            .toList();
        if (current.isEmpty()) {
            return null;
        }

        // Pick the store with the largest genuine drop against its own prior low.
        StorePrices best = null;
        double bestDiscount = 0.0;
        for (StorePrices store : current) {
            if (store.priorMin == null || store.priorMin <= 0 || store.currentMin >= store.priorMin) {
                continue;
            }
            double discount = (store.priorMin - store.currentMin) / store.priorMin;
            if (discount >= MIN_DISCOUNT && discount <= MAX_DISCOUNT && discount > bestDiscount) {
                bestDiscount = discount;
                best = store;
            }
        }
        if (best == null) {
            return null;
        }

        double rangeLow = current.get(0).currentMin;
        double rangeHigh = current.get(current.size() - 1).currentMin;
        List<String> storesCompared = current.stream()
            .map(store -> store.store)
            .limit(MAX_STORE_AVATARS)
            .toList();

        // Title and image come from the exact item that produced this sale, so the
        // picture and name always match the price. Fall back within the cluster
        // only when the winning row happens to lack one.
        String title = firstNotBlank(best.itemName, cluster.anyItemName());
        String imageUrl = firstNotBlank(best.imageUrl, cluster.anyImageUrl());

        return new DealDto(
            cluster.productId,
            title,
            cluster.category,
            round2(best.currentMin),
            round2(best.priorMin),
            (int) Math.round(bestDiscount * 100),
            best.store,
            current.size(),
            round2(rangeLow),
            round2(rangeHigh),
            imageUrl,
            storesCompared,
            false);
    }

    // Key that unites the same physical item across stores within one search term.
    // Returns null when there is no product to attribute the item to or no usable
    // name to normalize, so such rows never form a deal.
    private String clusterKey(Long productId, String itemName) {
        if (productId == null) {
            return null;
        }
        String normalized = normalizeItemName(itemName);
        if (normalized.isEmpty()) {
            return null;
        }
        return productId + ":" + normalized;
    }

    // Order-independent normal form: lowercase, drop pack-size tokens and
    // punctuation, then sort the remaining words so "Aquelle Still 500ml" and
    // "Still Water Aquelle" collapse to the same key.
    private String normalizeItemName(String itemName) {
        if (itemName == null) {
            return "";
        }
        String cleaned = itemName.toLowerCase()
            .replaceAll("\\b\\d+(?:[.,]\\d+)?\\s*(?:ml|l|litre|liter|kg|g|gram)s?\\b", " ")
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        String[] tokens = cleaned.split("\\s+");
        java.util.Arrays.sort(tokens);
        return String.join(" ", tokens);
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

    private static Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static final class ItemCluster {
        private final Long productId;
        private final String category;
        private final List<StorePrices> stores = new ArrayList<>();

        private ItemCluster(Long productId, String category) {
            this.productId = productId;
            this.category = category;
        }

        private String anyItemName() {
            return stores.stream()
                .map(store -> store.itemName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("");
        }

        private String anyImageUrl() {
            return stores.stream()
                .map(store -> store.imageUrl)
                .filter(image -> image != null && !image.isBlank())
                .findFirst()
                .orElse("");
        }
    }

    private static final class StorePrices {
        private final String store;
        private final Double currentMin;
        private final Double priorMin;
        private final String itemName;
        private final String imageUrl;

        private StorePrices(String store, Double currentMin, Double priorMin, String itemName, String imageUrl) {
            this.store = store;
            this.currentMin = currentMin;
            this.priorMin = priorMin;
            this.itemName = itemName;
            this.imageUrl = imageUrl;
        }
    }
}
