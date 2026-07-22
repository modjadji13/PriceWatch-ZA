package com.pricewatch.dto;

import java.util.List;

/**
 * One real, history-derived deal for the home page. currentPrice is the cheapest
 * price seen for this search term at {@code store} in the recent window; oldPrice
 * is the store's own cheapest over the prior baseline window. estimated is always
 * false because only live scraped prices are ever recorded as history.
 */
public record DealDto(
    Long productId,
    String title,
    String category,
    double currentPrice,
    double oldPrice,
    int discountPercent,
    String store,
    int storeCount,
    double rangeLow,
    double rangeHigh,
    String imageUrl,
    List<String> storesCompared,
    boolean estimated
) {
}
