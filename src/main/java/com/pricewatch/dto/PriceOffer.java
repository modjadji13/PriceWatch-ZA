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
    List<StoreLogo> topStoreLogos
) {
    public PriceOffer(String store, double amount, boolean estimated, String logoUrl) {
        this(store, amount, estimated, logoUrl, "", "", "");
    }

    public PriceOffer(
        String store,
        double amount,
        boolean estimated,
        String logoUrl,
        String productName,
        String productImageUrl,
        String productCategory
    ) {
        this(store, amount, estimated, logoUrl, productName, productImageUrl, productCategory, List.of());
    }

    public PriceOffer withTopStoreLogos(List<StoreLogo> topStoreLogos) {
        return new PriceOffer(
            store,
            amount,
            estimated,
            logoUrl,
            productName,
            productImageUrl,
            productCategory,
            topStoreLogos
        );
    }

    public record StoreLogo(String store, String logoUrl) {
    }
}
