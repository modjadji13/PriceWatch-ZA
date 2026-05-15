package com.pricewatch.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GenericScraper {
    private static final Logger logger = LoggerFactory.getLogger(GenericScraper.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?i)(?:R|ZAR)\\s?\\d{1,4}(?:[\\s,]\\d{3})*(?:[.,]\\d{2})?");
    private static final int MAX_PROMPT_TEXT_LENGTH = 6000;

    private final AiPriceExtractor aiExtractor;
    private List<StoreConfig> knownStores = new ArrayList<>();

    @Value("${scraper.ai-discovery.enabled:false}")
    private boolean aiDiscoveryEnabled;

    public GenericScraper(AiPriceExtractor aiExtractor) {
        this.aiExtractor = aiExtractor;
    }

    @PostConstruct
    public void loadStores() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ClassPathResource resource = new ClassPathResource("stores.json");

            Map<String, Object> data = mapper.readValue(
                resource.getInputStream(),
                mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );

            knownStores = mapper.convertValue(
                data.get("stores"),
                mapper.getTypeFactory().constructCollectionType(List.class, StoreConfig.class)
            );

            logger.info("Loaded {} stores", knownStores.size());
        } catch (Exception e) {
            logger.warn("Failed to load stores.json: {}", e.getMessage());
            knownStores = new ArrayList<>();
        }
    }

    public Map<String, Double> scrapeByCategory(String productName, String category) {
        String normalizedCategory = category == null || category.isBlank() ? "GROCERY" : category;
        Map<String, Double> results = new HashMap<>();

        List<StoreConfig> stores = knownStores.stream()
            .filter(store -> store.getCategory() != null)
            .filter(store -> store.getCategory().equalsIgnoreCase(normalizedCategory))
            .toList();

        List<StoreConfig> aiStores = aiDiscoveryEnabled
            ? aiExtractor.discoverStores(normalizedCategory)
            : List.of();

        Set<String> seen = new HashSet<>();
        List<StoreConfig> allStores = new ArrayList<>();

        for (StoreConfig store : stores) {
            addUniqueStore(seen, allStores, store);
        }
        for (StoreConfig store : aiStores) {
            addUniqueStore(seen, allStores, store);
        }

        for (StoreConfig store : allStores) {
            double price = scrapeStore(store, productName);
            if (price > 0) {
                results.put(store.getName(), price);
            }
        }

        return results;
    }

    private double scrapeStore(StoreConfig store, String productName) {
        try {
            String url = buildSearchUrl(store.getSearchUrl(), productName);

            Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-ZA,en;q=0.9")
                .referrer("https://www.google.com/")
                .followRedirects(true)
                .ignoreContentType(true)
                .timeout(15000)
                .execute();

            String relevantText = extractRelevantContent(response, productName);

            if (relevantText.isBlank()) {
                logger.warn("{} returned no readable product or price text", store.getName());
                return 0.0;
            }

            return aiExtractor.extractPrice(relevantText, productName);
        } catch (Exception e) {
            logger.warn("{} scrape failed: {}", store.getName(), e.getMessage());
            return 0.0;
        }
    }

    private String extractRelevantContent(Connection.Response response, String productName) throws IOException {
        String body = response.body();
        String contentType = response.contentType() == null ? "" : response.contentType().toLowerCase();
        String trimmedBody = body == null ? "" : body.trim();

        if (contentType.contains("json") || trimmedBody.startsWith("{") || trimmedBody.startsWith("[")) {
            return extractRelevantRawText(trimmedBody, productName);
        }

        return extractRelevantPageText(response.parse(), productName);
    }

    private String buildSearchUrl(String searchUrl, String productName) {
        String encodedProductName = URLEncoder.encode(productName, StandardCharsets.UTF_8);

        if (searchUrl.contains("{query}")) {
            return searchUrl.replace("{query}", encodedProductName);
        }

        if (searchUrl.contains("%s")) {
            return searchUrl.formatted(encodedProductName);
        }

        if (searchUrl.endsWith("=") || searchUrl.endsWith("/") || searchUrl.endsWith("?") || searchUrl.endsWith("&")) {
            return searchUrl + encodedProductName;
        }

        String separator = searchUrl.contains("?") ? "&" : "?";
        return searchUrl + separator + "q=" + encodedProductName;
    }

    private String extractRelevantPageText(Document doc, String productName) {
        Set<String> snippets = new LinkedHashSet<>();
        String productToken = firstSearchToken(productName);

        addIfUseful(snippets, doc.title(), productToken);

        doc.select("script[type=application/ld+json]").forEach(script ->
            addIfUseful(snippets, script.data(), productToken)
        );

        doc.select("script,style,noscript,svg,link,meta,nav,footer").remove();

        for (Element element : doc.select("body *")) {
            addIfUseful(snippets, element.ownText(), productToken);
            if (lengthOf(snippets) >= MAX_PROMPT_TEXT_LENGTH) {
                break;
            }
        }

        String bodyText = doc.body() == null ? "" : doc.body().text();
        addWindowsAroundMatches(snippets, bodyText, Pattern.compile(Pattern.quote(productToken), Pattern.CASE_INSENSITIVE));
        addWindowsAroundMatches(snippets, bodyText, PRICE_PATTERN);

        String joined = String.join("\n", snippets).trim();
        if (joined.length() > MAX_PROMPT_TEXT_LENGTH) {
            return joined.substring(0, MAX_PROMPT_TEXT_LENGTH);
        }

        return joined;
    }

    private String extractRelevantRawText(String rawText, String productName) {
        Set<String> snippets = new LinkedHashSet<>();
        String productToken = firstSearchToken(productName);

        addWindowsAroundMatches(snippets, rawText, Pattern.compile(Pattern.quote(productToken), Pattern.CASE_INSENSITIVE));
        addWindowsAroundMatches(snippets, rawText, PRICE_PATTERN);

        String joined = String.join("\n", snippets).trim();
        if (joined.length() > MAX_PROMPT_TEXT_LENGTH) {
            return joined.substring(0, MAX_PROMPT_TEXT_LENGTH);
        }

        return joined;
    }

    private void addIfUseful(Set<String> snippets, String text, String productToken) {
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return;
        }

        String lower = normalized.toLowerCase();
        boolean mentionsProduct = lower.contains(productToken.toLowerCase());
        boolean mentionsPrice = PRICE_PATTERN.matcher(normalized).find();

        if (mentionsProduct || mentionsPrice) {
            snippets.add(normalized);
        }
    }

    private void addWindowsAroundMatches(Set<String> snippets, String text, Pattern pattern) {
        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            return;
        }

        Matcher matcher = pattern.matcher(normalized);

        while (matcher.find() && lengthOf(snippets) < MAX_PROMPT_TEXT_LENGTH) {
            int start = Math.max(0, matcher.start() - 220);
            int end = Math.min(normalized.length(), matcher.end() + 220);
            snippets.add(normalized.substring(start, end).trim());
        }
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String firstSearchToken(String productName) {
        if (productName == null || productName.isBlank()) {
            return "__missing_product_name__";
        }

        return productName.trim().split("\\s+")[0];
    }

    private int lengthOf(Set<String> snippets) {
        return snippets.stream().mapToInt(String::length).sum();
    }

    private void addUniqueStore(Set<String> seen, List<StoreConfig> stores, StoreConfig store) {
        if (store.getName() == null || store.getSearchUrl() == null || store.getCategory() == null) {
            return;
        }

        String key = store.getName().trim().toLowerCase();
        if (seen.add(key)) {
            stores.add(store);
        }
    }
}
