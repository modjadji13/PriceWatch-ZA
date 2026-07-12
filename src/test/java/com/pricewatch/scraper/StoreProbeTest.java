package com.pricewatch.scraper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live diagnostic: runs every store in stores.json through the real
 * scrapeStore path (thorough mode) and prints how many products each returns.
 * Not an assertion test; it never fails, it just reports.
 *
 * Hits real store websites, so it is skipped unless explicitly requested:
 * mvn test -Dtest=StoreProbeTest -DprobeStores=true
 */
@EnabledIfSystemProperty(named = "probeStores", matches = "true")
class StoreProbeTest {

    private static final Map<String, String> QUERY_BY_CATEGORY = Map.of(
        "GROCERY", "milk",
        "GENERAL", "coffee",
        "HEALTH", "vitamin c",
        "BEAUTY", "shampoo",
        "ELECTRONICS", "laptop"
    );

    @Test
    void probeAllStores() throws Exception {
        GenericScraper scraper = new GenericScraper();
        scraper.loadStores();

        java.lang.reflect.Field storesField = GenericScraper.class.getDeclaredField("knownStores");
        storesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<StoreConfig> stores = (List<StoreConfig>) storesField.get(scraper);

        Method scrapeStore = GenericScraper.class.getDeclaredMethod(
            "scrapeStore", StoreConfig.class, String.class, String.class, boolean.class);
        scrapeStore.setAccessible(true);

        Map<String, String> report = new LinkedHashMap<>();
        for (StoreConfig store : stores) {
            String key = store.getName() + " [" + store.getCategory() + "]";
            if (store.getParser() == null) {
                report.put(key, "NO PARSER (never scraped, curated fallback only)");
                continue;
            }

            String query = QUERY_BY_CATEGORY.getOrDefault(store.getCategory(), "milk");
            long start = System.currentTimeMillis();
            @SuppressWarnings("unchecked")
            List<Object> products = (List<Object>) scrapeStore.invoke(scraper, store, query, store.getCategory(), true);
            long elapsed = System.currentTimeMillis() - start;

            String sample = products.isEmpty() ? "" : " | sample: " + products.get(0);
            report.put(key, products.size() + " products for '" + query + "' in " + elapsed + "ms" + sample);
        }

        System.out.println("\n===== STORE PROBE REPORT =====");
        report.forEach((store, result) -> System.out.println(store + " -> " + result));
        System.out.println("===== END REPORT =====\n");
    }
}
