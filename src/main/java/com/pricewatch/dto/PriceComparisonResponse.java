package com.pricewatch.dto;

import java.util.List;

public record PriceComparisonResponse(
    String product,
    String category,
    ProductDetails details,
    List<PriceOffer> prices
) {
}
