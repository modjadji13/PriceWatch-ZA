package com.pricewatch.dto;

import java.util.List;

public record PriceOffer(
    String store,
    double amount,
    boolean estimated,
    String logoUrl,
    String productName,
    String productImageUrl,
    String productCategory,
    String productDescription,
    List<StoreOffer> storeOffers
) {
    public PriceOffer(
        String store,
        double amount,
        boolean estimated,
        String logoUrl,
        String productName,
        String productImageUrl,
        String productCategory
    ) {
        this(store, amount, estimated, logoUrl, productName, productImageUrl, productCategory, "", List.of());
    }

    public PriceOffer(
        String store,
        double amount,
        boolean estimated,
        String logoUrl,
        String productName,
        String productImageUrl,
        String productCategory,
        String productDescription
    ) {
        this(store, amount, estimated, logoUrl, productName, productImageUrl, productCategory, productDescription, List.of());
    }

    public PriceOffer withStoreOffers(List<StoreOffer> storeOffers) {
        return new PriceOffer(
            store,
            amount,
            estimated,
            logoUrl,
            productName,
            productImageUrl,
            productCategory,
            productDescription,
            storeOffers
        );
    }

    /** One store's price for the product this offer represents. */
    public record StoreOffer(String store, double amount, String logoUrl) {
    }
}
