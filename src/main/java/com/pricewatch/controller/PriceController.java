package com.pricewatch.controller;

import com.pricewatch.model.Price;
import com.pricewatch.service.PriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prices")
public class PriceController {
    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/compare")
    public Map<String, Double> compare(
        @RequestParam String product,
        @RequestParam(defaultValue = "GROCERY") String category) {
        return priceService.comparePrices(product, category);
    }

    @GetMapping("/history")
    public List<Price> history(@RequestParam Long productId) {
        return priceService.getPriceHistory(productId);
    }

    @GetMapping("/lowest")
    public ResponseEntity<Price> lowest(@RequestParam Long productId) {
        return priceService.getLowestPrice(productId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/highest")
    public ResponseEntity<Price> highest(@RequestParam Long productId) {
        return priceService.getHighestPrice(productId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
