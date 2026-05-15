package com.pricewatch.service;

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
    public Map<String, Double> comparePrices(String productName, String category) {
        String normalizedCategory = category == null || category.isBlank() ? "GROCERY" : category;
        Map<String, Double> results = genericScraper.scrapeByCategory(productName, normalizedCategory);

        Product product = productRepository
            .findByNameIgnoreCaseAndCategoryIgnoreCase(productName, normalizedCategory)
            .orElseGet(() -> productRepository.save(new Product(productName, normalizedCategory)));

        for (Map.Entry<String, Double> result : results.entrySet()) {
            Price price = new Price(
                result.getKey(),
                result.getValue(),
                LocalDateTime.now(),
                product
            );
            priceRepository.save(price);
        }

        return results;
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
}
