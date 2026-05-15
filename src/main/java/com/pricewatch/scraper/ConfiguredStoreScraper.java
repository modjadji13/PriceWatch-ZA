package com.pricewatch.scraper;

import com.pricewatch.model.Price;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ConfiguredStoreScraper implements PriceScraper {
    private final GenericScraper genericScraper;

    public ConfiguredStoreScraper(GenericScraper genericScraper) {
        this.genericScraper = genericScraper;
    }

    @Override
    public String getStoreName() {
        return "Configured Stores";
    }

    @Override
    public String getCategory() {
        return "GROCERY";
    }

    @Override
    public List<Price> scrape(String productName) {
        return scrape(productName, getCategory());
    }

    public List<Price> scrape(String productName, String category) {
        Map<String, Double> results = genericScraper.scrapeByCategory(productName, category);

        return results.entrySet()
            .stream()
            .map(result -> {
                Price price = new Price();
                price.setStore(result.getKey());
                price.setAmount(result.getValue());
                price.setRecordedAt(LocalDateTime.now());
                return price;
            })
            .toList();
    }
}
