package com.pricewatch.dto;

public record ProductDetails(
    String name,
    String category,
    String imageUrl,
    String description,
    String sourceStore
) {
}
