package com.pricewatch.dto;

public record PriceOffer(
    String store,
    double amount,
    boolean estimated,
    String logoUrl
) {
}
