package com.pricewatch.service;

import com.pricewatch.dto.PriceComparisonResponse;
import com.pricewatch.dto.PriceOffer;
import com.pricewatch.model.Price;
import com.pricewatch.model.Product;
import com.pricewatch.repository.PriceRepository;
import com.pricewatch.repository.ProductRepository;
import com.pricewatch.scraper.GenericScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PriceService {
    private static final Logger logger = LoggerFactory.getLogger(PriceService.class);

    private final GenericScraper genericScraper;
    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;

    public PriceService(
        GenericScraper genericScraper,
        ProductRepository productRepository,
        PriceRepository priceRepository) {
        this.genericScraper = genericScraper;
        this.productRepository = productRepository;
        this.priceRepository = priceRepository;
    }

    @Transactional
    public PriceComparisonResponse comparePrices(String productName, String category) {
        String normalizedCategory = category == null || category.isBlank() ? "GROCERY" : category;
        PriceComparisonResponse comparison = genericScraper.scrapeProductComparison(productName, normalizedCategory);

        Product product = productRepository
            .findByNameIgnoreCaseAndCategoryIgnoreCase(productName, normalizedCategory)
            .orElseGet(() -> productRepository.save(new Product(productName, normalizedCategory)));

        for (PriceOffer offer : comparison.prices()) {
            if (offer.estimated()) {
                continue;
            }

            Price price = new Price(
                offer.store(),
                offer.amount(),
                LocalDateTime.now(),
                product
            );
            priceRepository.save(price);
        }

        return comparison;
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

    public void updateAllPrices() {
        List<Product> products = productRepository.findAll();
        logger.info("Updating prices for {} products", products.size());

        for (Product product : products) {
            comparePrices(product.getName(), product.getCategory());
        }
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
