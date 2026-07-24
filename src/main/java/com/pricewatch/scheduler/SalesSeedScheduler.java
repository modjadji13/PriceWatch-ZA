package com.pricewatch.scheduler;

import com.pricewatch.service.PriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keeps the home page's "sale items" populated by regularly scraping a spread of
 * common products across the stores. Each scrape caches the store-advertised
 * prices (including any "was" price), and {@link com.pricewatch.service.DealService}
 * turns the current markdowns into the sale feed. Because the stores always have
 * something on promotion, this list reliably yields real, current deals without
 * waiting for users to search first.
 */
@Component
public class SalesSeedScheduler {
    private static final Logger logger = LoggerFactory.getLogger(SalesSeedScheduler.class);

    // Terms chosen to span the catalogue so a variety of real markdowns surface.
    // Every store with a parser is scraped for each term regardless of category,
    // so the category here only steers the curated fallback, not coverage.
    private static final List<Seed> SEEDS = List.of(
        new Seed("coffee", "GROCERY"),
        new Seed("chocolate", "GROCERY"),
        new Seed("washing powder", "GROCERY"),
        new Seed("cooking oil", "GROCERY"),
        new Seed("cereal", "GROCERY"),
        new Seed("rice", "GROCERY"),
        new Seed("chips", "GROCERY"),
        new Seed("cooldrink", "GROCERY"),
        new Seed("kettle", "HOUSEHOLD"),
        new Seed("air fryer", "HOUSEHOLD"),
        new Seed("microwave", "HOUSEHOLD"),
        new Seed("toilet paper", "HOUSEHOLD"),
        new Seed("batteries", "HOUSEHOLD"),
        new Seed("tv", "ELECTRONICS"),
        new Seed("laptop", "ELECTRONICS"),
        new Seed("headphones", "ELECTRONICS"),
        new Seed("speaker", "ELECTRONICS"),
        new Seed("vitamins", "HEALTH"),
        new Seed("shampoo", "HEALTH"),
        new Seed("toothpaste", "HEALTH"),
        new Seed("perfume", "BEAUTY"),
        new Seed("body lotion", "BEAUTY"),
        new Seed("deodorant", "BEAUTY")
    );

    private final PriceService priceService;

    public SalesSeedScheduler(PriceService priceService) {
        this.priceService = priceService;
    }

    // initialDelay is short so the home page fills soon after boot; the hourly
    // rate keeps advertised prices reasonably fresh. comparePrices is DB-first
    // and refreshes in the background, so repeated runs are cheap once cached.
    @Scheduled(fixedRate = 3_600_000, initialDelay = 20_000)
    public void refreshSales() {
        logger.info("Refreshing sale feed from {} seed searches", SEEDS.size());
        for (Seed seed : SEEDS) {
            try {
                priceService.comparePrices(seed.term(), seed.category());
            } catch (Exception e) {
                logger.warn("Sale seed scrape failed for '{}': {}", seed.term(), e.getMessage());
            }
        }
    }

    private record Seed(String term, String category) {
    }
}
