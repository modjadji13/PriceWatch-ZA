package com.pricewatch.scheduler;

import com.pricewatch.model.Product;
import com.pricewatch.repository.ProductRepository;
import com.pricewatch.service.PriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceUpdateScheduler {
    private static final Logger logger = LoggerFactory.getLogger(PriceUpdateScheduler.class);

    private final ProductRepository productRepository;
    private final PriceService priceService;

    public PriceUpdateScheduler(ProductRepository productRepository, PriceService priceService) {
        this.productRepository = productRepository;
        this.priceService = priceService;
    }

    @Scheduled(fixedRate = 3_600_000)
    public void updatePrices() {
        List<Product> products = productRepository.findAll();
        logger.info("Starting scheduled price update for {} products", products.size());

        for (Product product : products) {
            try {
                priceService.comparePrices(product.getName(), product.getCategory());
            } catch (Exception e) {
                logger.warn("Scheduled update failed for product {}: {}", product.getName(), e.getMessage());
            }
        }
    }
}
