package com.pricewatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewatch.dto.PriceComparisonResponse;
import com.pricewatch.dto.PriceOffer;
import com.pricewatch.model.Price;
import com.pricewatch.model.Product;
import com.pricewatch.model.SearchResult;
import com.pricewatch.repository.PriceRepository;
import com.pricewatch.repository.ProductRepository;
import com.pricewatch.repository.SearchResultRepository;
import com.pricewatch.scraper.GenericScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

@Service
public class PriceService {
    private static final Logger logger = LoggerFactory.getLogger(PriceService.class);
    private static final Duration RESULT_FRESHNESS_TTL = Duration.ofMinutes(10);

    private final GenericScraper genericScraper;
    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final SearchResultRepository searchResultRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> refreshingKeys = ConcurrentHashMap.newKeySet();
    private final ExecutorService refreshExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdownRefreshExecutor() {
        refreshExecutor.shutdownNow();
    }

    public PriceService(
        GenericScraper genericScraper,
        ProductRepository productRepository,
        PriceRepository priceRepository,
        SearchResultRepository searchResultRepository) {
        this.genericScraper = genericScraper;
        this.productRepository = productRepository;
        this.priceRepository = priceRepository;
        this.searchResultRepository = searchResultRepository;
    }

    // DB-first: a previously seen search is answered instantly from the stored
    // result while a background re-scrape overwrites it for next time. Only a
    // never-seen search pays the live scrape latency. Deliberately not
    // @Transactional: scraping can take seconds and must not pin a connection.
    public PriceComparisonResponse comparePrices(String productName, String category) {
        String normalizedCategory = category == null || category.isBlank() ? "GROCERY" : category;
        String term = normalizedTerm(productName);
        String categoryKey = normalizedTerm(normalizedCategory);

        Optional<SearchResult> saved = searchResultRepository.findBySearchTermAndCategory(term, categoryKey);
        if (saved.isPresent()) {
            PriceComparisonResponse stored = deserialize(saved.get());
            if (stored != null) {
                if (isStale(saved.get())) {
                    refreshInBackground(productName, normalizedCategory, term, categoryKey);
                }
                return stored;
            }
        }

        return scrapeAndStore(productName, normalizedCategory, term, categoryKey);
    }

    private boolean isStale(SearchResult saved) {
        return saved.getScrapedAt().plus(RESULT_FRESHNESS_TTL).isBefore(LocalDateTime.now());
    }

    private void refreshInBackground(String productName, String category, String term, String categoryKey) {
        String refreshKey = categoryKey + ":" + term;
        if (!refreshingKeys.add(refreshKey)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                scrapeAndStore(productName, category, term, categoryKey);
            } catch (Exception e) {
                logger.warn("Background refresh failed for '{}': {}", productName, e.getMessage());
            } finally {
                refreshingKeys.remove(refreshKey);
            }
        }, refreshExecutor);
    }

    private PriceComparisonResponse scrapeAndStore(String productName, String category, String term, String categoryKey) {
        PriceComparisonResponse comparison = genericScraper.scrapeProductComparison(productName, category);

        boolean curatedFallback = comparison.details() != null
            && "curated".equalsIgnoreCase(comparison.details().sourceStore());

        // Empty scrapes never overwrite a stored result: a temporarily down store
        // set should not erase yesterday's usable comparison.
        if (!comparison.prices().isEmpty()) {
            storeSearchResult(term, categoryKey, comparison);
        }

        if (!curatedFallback) {
            saveLivePrices(productName, category, comparison);
        }

        return comparison;
    }

    private void storeSearchResult(String term, String categoryKey, PriceComparisonResponse comparison) {
        try {
            String json = objectMapper.writeValueAsString(comparison);
            SearchResult row = searchResultRepository
                .findBySearchTermAndCategory(term, categoryKey)
                .orElseGet(() -> new SearchResult(term, categoryKey, json, LocalDateTime.now()));
            row.setResultJson(json);
            row.setScrapedAt(LocalDateTime.now());
            searchResultRepository.save(row);
        } catch (Exception e) {
            logger.warn("Failed to store search result for '{}': {}", term, e.getMessage());
        }
    }

    private PriceComparisonResponse deserialize(SearchResult saved) {
        try {
            return objectMapper.readValue(saved.getResultJson(), PriceComparisonResponse.class);
        } catch (Exception e) {
            logger.warn("Stored search result for '{}' is unreadable; re-scraping: {}", saved.getSearchTerm(), e.getMessage());
            return null;
        }
    }

    private String normalizedTerm(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private void saveLivePrices(String productName, String category, PriceComparisonResponse comparison) {
        List<Price> livePrices = new ArrayList<>();
        LocalDateTime recordedAt = LocalDateTime.now();

        Product product = productRepository
            .findByNameIgnoreCaseAndCategoryIgnoreCase(productName, category)
            .orElseGet(() -> productRepository.save(new Product(productName, category)));

        for (PriceOffer offer : comparison.prices()) {
            if (!offer.estimated()) {
                livePrices.add(new Price(offer.store(), offer.amount(), recordedAt, product));
            }
        }

        priceRepository.saveAll(livePrices);
    }

    private String comparisonCacheKey(String productName, String category) {
        String normalizedProduct = productName == null ? "" : productName.trim().toLowerCase();
        String normalizedCategory = category == null ? "" : category.trim().toLowerCase();
        return normalizedCategory + ":" + normalizedProduct;
    }

    public List<Price> getPriceHistory(Long productId) {
        return priceRepository.findByProductIdOrderByRecordedAtDesc(productId);
    }

    public Optional<Price> getLowestPrice(Long productId) {
        return priceRepository.findTopByProductIdOrderByAmountAsc(productId);
    }

    public Optional<Price> getHighestPrice(Long productId) {
        return priceRepository.findTopByProductIdOrderByAmountDesc(productId);
    }

    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategoryIgnoreCase(category);
    }

    @Transactional
    public void saveAgentResults(Map<String, Object> priceData) {
        String productName = String.valueOf(priceData.getOrDefault("product", ""));
        if (productName.isBlank()) {
            logger.warn("Agent result ignored because product is missing");
            return;
        }

        String category = String.valueOf(priceData.getOrDefault("category", "GROCERY"));
        Product product = productRepository
            .findByNameIgnoreCaseAndCategoryIgnoreCase(productName, category)
            .orElseGet(() -> productRepository.save(new Product(productName, category)));

        Object allPrices = priceData.get("all_prices");
        if (!(allPrices instanceof List<?> prices)) {
            logger.warn("Agent result ignored because all_prices is missing or invalid");
            return;
        }

        for (Object item : prices) {
            if (!(item instanceof Map<?, ?> priceMap)) {
                continue;
            }

            Object storeValue = priceMap.get("store");
            String store = storeValue == null ? "" : String.valueOf(storeValue);
            double amount = toDouble(priceMap.get("price"));

            if (store.isBlank() || amount <= 0) {
                continue;
            }

            priceRepository.save(new Price(store, amount, LocalDateTime.now(), product));
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }

        return 0.0;
    }

}
