package com.pricewatch.scraper;

public class StoreConfig {
    private String name;
    private String searchUrl;
    private String category;
    private String logoUrl;
    private ParserConfig parser;

    public StoreConfig() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSearchUrl() {
        return searchUrl;
    }

    public void setSearchUrl(String searchUrl) {
        this.searchUrl = searchUrl;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public ParserConfig getParser() {
        return parser;
    }

    public void setParser(ParserConfig parser) {
        this.parser = parser;
    }

    /**
     * How to read products off this store's search results. Stores without a
     * parser are listed for curated fallback pricing but never scraped.
     * type "takealot-api" and "shoprite-frames" use dedicated code paths;
     * type "css" (the default) reads products with the selectors below.
     */
    public static class ParserConfig {
        private String type = "css";
        private String card;
        private String name;
        private String price;

        public ParserConfig() {
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getCard() {
            return card;
        }

        public void setCard(String card) {
            this.card = card;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPrice() {
            return price;
        }

        public void setPrice(String price) {
            this.price = price;
        }
    }
}
