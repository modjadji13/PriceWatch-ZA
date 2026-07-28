package com.pricewatch.scraper;

import com.pricewatch.dto.PriceOffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the cross-store product grouping rules: identical products
 * merge into one "found at N stores" card, while different sizes, different
 * variants, and wildly-different prices are kept as separate cards.
 */
class GenericScraperGroupingTest {

    private final GenericScraper scraper = new GenericScraper();

    private static PriceOffer offer(String store, String name, double amount) {
        return new PriceOffer(store, amount, false, "", name, "", "GROCERY");
    }

    @Test
    void identicalProductAcrossStoresMergesIntoOneCard() {
        List<PriceOffer> grouped = scraper.groupOffersAcrossStores(List.of(
            offer("Shoprite", "Tastic Long Grain Parboiled Rice 1kg", 22.99),
            offer("Checkers", "Tastic Long Grain Parboiled Rice 1kg", 24.99)
        ));

        assertEquals(1, grouped.size(), "same product + size at two stores should be one card");
        assertEquals(2, grouped.get(0).storeOffers().size(), "the card should list both stores");
        assertEquals(22.99, grouped.get(0).amount(), 0.001, "the card shows the cheapest price");
    }

    @Test
    void differentSizesDoNotMerge() {
        List<PriceOffer> grouped = scraper.groupOffersAcrossStores(List.of(
            offer("Pick n Pay", "Tastic Rice 500g", 18.99),
            offer("Shoprite", "Tastic Rice 1kg", 26.99)
        ));

        assertEquals(2, grouped.size(), "500g and 1kg are different products");
    }

    @Test
    void differentVariantsDoNotMerge() {
        List<PriceOffer> grouped = scraper.groupOffersAcrossStores(List.of(
            offer("Shoprite", "Tastic Long Grain White Rice 2kg", 39.99),
            offer("Checkers", "Tastic Wholegrain Brown Rice 2kg", 42.99)
        ));

        assertEquals(2, grouped.size(), "white vs brown are different variants, not one product");
    }

    @Test
    void sameVariantAcrossStoresStillMerges() {
        List<PriceOffer> grouped = scraper.groupOffersAcrossStores(List.of(
            offer("Shoprite", "Tastic Long Grain White Rice 2kg", 39.99),
            offer("Checkers", "Tastic Long Grain White Rice 2kg", 41.99)
        ));

        assertEquals(1, grouped.size(), "the same variant at two stores is one card");
        assertEquals(2, grouped.get(0).storeOffers().size());
    }

    @Test
    void wildlyDifferentPriceDoesNotMerge() {
        // The second offer has no parsed size (a wildcard that would merge on
        // name alone), but a ~37x price gap means it is a bulk/combo product and
        // must not pollute the 500g card's range.
        List<PriceOffer> grouped = scraper.groupOffersAcrossStores(List.of(
            offer("Shoprite", "Tastic Rice 500g", 15.99),
            offer("Takealot", "Tastic Rice", 599.00)
        ));

        assertEquals(2, grouped.size(), "a 37x-priced item must not merge into the 500g card");
    }

    @Test
    void mergedCardRangeStaysWithinPriceBand() {
        // 16 -> 60 -> 174: the mid-priced item must not bridge the cheap unit to
        // the expensive one; every card's own range stays within the ratio band.
        List<PriceOffer> grouped = scraper.groupOffersAcrossStores(List.of(
            offer("Shoprite", "Selati White Sugar 500g", 16.49),
            offer("Checkers", "Selati White Sugar", 60.00),
            offer("Takealot", "Selati White Sugar", 174.00)
        ));

        for (PriceOffer card : grouped) {
            double low = card.storeOffers().stream().mapToDouble(PriceOffer.StoreOffer::amount).min().orElse(card.amount());
            double high = card.storeOffers().stream().mapToDouble(PriceOffer.StoreOffer::amount).max().orElse(card.amount());
            if (low > 0) {
                assertEquals(true, high / low <= 4.0,
                    "card range " + low + "-" + high + " should stay within 4x");
            }
        }
    }

    @Test
    void multipackDoesNotMergeWithSingle() {
        // Prices are within the 4x band on purpose, so only the pack quantity
        // (not the price guard) keeps them apart.
        List<PriceOffer> grouped = scraper.groupOffersAcrossStores(List.of(
            offer("Checkers", "Coca-Cola Original 300ml", 20.00),
            offer("Checkers", "Coca-Cola Original 3 x 300ml", 55.00)
        ));

        assertEquals(2, grouped.size(), "a 3-pack is a different product from a single 300ml");
    }

    @Test
    void sameMultipackAcrossStoresMerges() {
        List<PriceOffer> grouped = scraper.groupOffersAcrossStores(List.of(
            offer("Checkers", "Coca-Cola Original 6 x 300ml", 59.99),
            offer("Shoprite", "Coca-Cola Original 6 x 300ml", 62.99)
        ));

        assertEquals(1, grouped.size(), "the same 6-pack at two stores is one card");
        assertEquals(2, grouped.get(0).storeOffers().size());
    }

    @Test
    void caseAndSetOfAreTreatedAsMultipacks() {
        // "Set of 12" and a "Case" are multipacks and must not merge with a single.
        List<PriceOffer> grouped = scraper.groupOffersAcrossStores(List.of(
            offer("Woolworths", "Coca-Cola Original 300ml", 9.99),
            offer("Takealot", "Coca-Cola Original 300ml Set of 12", 199.00),
            offer("Takealot", "Coca-Cola Original 300ml Case", 229.00)
        ));

        assertEquals(3, grouped.size(), "single, 'set of 12' and 'case' are three products");
    }
}
