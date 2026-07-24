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
    String productUrl,
    // The store's advertised original ("was") price when this item is on sale,
    // else 0. Lets a live markdown be shown without any price history.
    double originalAmount,
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
        this(store, amount, estimated, logoUrl, productName, productImageUrl, productCategory, "", "", 0.0, List.of());
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
        this(store, amount, estimated, logoUrl, productName, productImageUrl, productCategory, productDescription, "", 0.0, List.of());
    }

    public PriceOffer(
        String store,
        double amount,
        boolean estimated,
        String logoUrl,
        String productName,
        String productImageUrl,
        String productCategory,
        String productDescription,
        String productUrl
    ) {
        this(store, amount, estimated, logoUrl, productName, productImageUrl, productCategory, productDescription, productUrl, 0.0, List.of());
    }

    public PriceOffer(
        String store,
        double amount,
        boolean estimated,
        String logoUrl,
        String productName,
        String productImageUrl,
        String productCategory,
        String productDescription,
        String productUrl,
        double originalAmount
    ) {
        this(store, amount, estimated, logoUrl, productName, productImageUrl, productCategory, productDescription, productUrl, originalAmount, List.of());
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
            productUrl,
            originalAmount,
            storeOffers
        );
    }

    /** One store's price for the product this offer represents. */
    public record StoreOffer(String store, double amount, String logoUrl) {
    }
}
