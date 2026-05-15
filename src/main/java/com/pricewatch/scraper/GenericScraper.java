package com.pricewatch.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GenericScraper {
    private static final Logger logger = LoggerFactory.getLogger(GenericScraper.class);

    private final AiPriceExtractor aiExtractor;
    private List<StoreConfig> knownStores = new ArrayList<>();

    public GenericScraper(AiPriceExtractor aiExtractor) {
        this.aiExtractor = aiExtractor;
    }

    @PostConstruct
    public void loadStores() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ClassPathResource resource = new ClassPathResource("stores.json");

            Map<String, Object> data = mapper.readValue(
                resource.getInputStream(),
                mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );

            knownStores = mapper.convertValue(
                data.get("stores"),
                mapper.getTypeFactory().constructCollectionType(List.class, StoreConfig.class)
            );

            logger.info("Loaded {} stores", knownStores.size());
        } catch (Exception e) {
            logger.warn("Failed to load stores.json: {}", e.getMessage());
            knownStores = new ArrayList<>();
        }
    }

    public Map<String, Double> scrapeByCategory(String productName, String category) {
        String normalizedCategory = category == null || category.isBlank() ? "GROCERY" : category;
        Map<String, Double> results = new HashMap<>();

        List<StoreConfig> stores = knownStores.stream()
            .filter(store -> store.getCategory() != null)
            .filter(store -> store.getCategory().equalsIgnoreCase(normalizedCategory))
            .toList();

        List<StoreConfig> aiStores = aiExtractor.discoverStores(normalizedCategory);

        Set<String> seen = new HashSet<>();
        List<StoreConfig> allStores = new ArrayList<>();

        for (StoreConfig store : stores) {
            addUniqueStore(seen, allStores, store);
        }
        for (StoreConfig store : aiStores) {
            addUniqueStore(seen, allStores, store);
        }

        for (StoreConfig store : allStores) {
            double price = scrapeStore(store, productName);
            if (price > 0) {
                results.put(store.getName(), price);
            }
        }

        return results;
    }

    private double scrapeStore(StoreConfig store, String productName) {
        try {
            String encodedProductName = URLEncoder.encode(productName, StandardCharsets.UTF_8);
            String url = store.getSearchUrl() + encodedProductName;

            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();

            return aiExtractor.extractPrice(doc.html(), productName);
        } catch (Exception e) {
            logger.warn("{} scrape failed: {}", store.getName(), e.getMessage());
            return 0.0;
        }
    }

    private void addUniqueStore(Set<String> seen, List<StoreConfig> stores, StoreConfig store) {
        if (store.getName() == null || store.getSearchUrl() == null || store.getCategory() == null) {
            return;
        }

        String key = store.getName().trim().toLowerCase();
        if (seen.add(key)) {
            stores.add(store);
        }
    }
}
