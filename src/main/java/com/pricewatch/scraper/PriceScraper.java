package com.pricewatch.scraper;

import com.pricewatch.model.Price;
import java.util.List;

public interface PriceScraper {
    String getStoreName();
    String getCategory();
    List<Price> scrape(String productName);
}