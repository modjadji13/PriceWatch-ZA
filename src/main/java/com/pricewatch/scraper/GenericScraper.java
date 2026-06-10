package com.pricewatch.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricewatch.dto.PriceComparisonResponse;
import com.pricewatch.dto.PriceOffer;
import com.pricewatch.dto.ProductDetails;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GenericScraper {
    private static final Logger logger = LoggerFactory.getLogger(GenericScraper.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?i)(?:R|ZAR)\\s?\\d{1,4}(?:[\\s,]\\d{3})*(?:[.,]\\d{2})?");
    private static final int MAX_PROMPT_TEXT_LENGTH = 6000;
    private static final int MIN_RESULTS_TO_SHOW = 5;
    private static final int SCRAPER_THREAD_COUNT = 5;
    private static final int STORE_TIMEOUT_MS = 3500;

    private final AiPriceExtractor aiExtractor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService scraperExecutor = Executors.newFixedThreadPool(SCRAPER_THREAD_COUNT);
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

    @PreDestroy
    public void shutdownScraperExecutor() {
        scraperExecutor.shutdownNow();
    }

    public Map<String, Double> scrapeByCategory(String productName, String category) {
        PriceComparisonResponse comparison = scrapeProductComparison(productName, category);
        Map<String, Double> results = new LinkedHashMap<>();

        for (PriceOffer offer : comparison.prices()) {
            results.put(offer.store(), offer.amount());
        }

        return results;
    }

    public PriceComparisonResponse scrapeProductComparison(String productName, String category) {
        String normalizedCategory = category == null || category.isBlank() ? "GROCERY" : category;
        List<PriceOffer> offers = new ArrayList<>();
        ProductDetails details = null;

        List<StoreConfig> stores = knownStores.stream()
            .filter(store -> store.getCategory() != null)
            .filter(store -> matchesCategory(store.getCategory(), normalizedCategory))
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

        if (!aiExtractor.isConfigured()) {
            return curatedComparison(productName, normalizedCategory, allStores);
        }

        List<StoreScrapeResult> scrapeResults = scrapeStores(allStores, productName, normalizedCategory);

        for (StoreScrapeResult result : scrapeResults) {
            StoreConfig store = result.store();
            for (ScrapedProduct scrapedProduct : result.products()) {
                if (scrapedProduct.amount() <= 0) {
                    continue;
                }

                offers.add(new PriceOffer(
                    store.getName(),
                    scrapedProduct.amount(),
                    false,
                    storeLogoUrl(store),
                    scrapedProduct.productName(),
                    scrapedProduct.imageUrl(),
                    firstNotBlank(scrapedProduct.category(), normalizedCategory),
                    scrapedProduct.description()
                ));
                if (details == null && scrapedProduct.hasDetails()) {
                    details = new ProductDetails(
                        productName,
                        normalizedCategory,
                        scrapedProduct.imageUrl(),
                        scrapedProduct.description(),
                        store.getName()
                    );
                }
            }
        }

        offers = lowestOfferPerProductVariant(offers, normalizedCategory);

        if (offers.isEmpty()) {
            PriceComparisonResponse fallbackComparison = curatedComparison(productName, normalizedCategory, allStores);
            if (!fallbackComparison.prices().isEmpty()) {
                return fallbackComparison;
            }
        }

        if (details == null) {
            details = fallbackDetails(productName, normalizedCategory);
        }

        return new PriceComparisonResponse(productName, normalizedCategory, details, offers);
    }

    private List<StoreScrapeResult> scrapeStores(List<StoreConfig> stores, String productName, String category) {
        return stores.stream()
            .map(store -> CompletableFuture.supplyAsync(
                () -> new StoreScrapeResult(store, scrapeStore(store, productName, category)),
                scraperExecutor
            ))
            .map(CompletableFuture::join)
            .toList();
    }

    private List<ScrapedProduct> scrapeStore(StoreConfig store, String productName, String category) {
        try {
            String url = buildSearchUrl(store.getSearchUrl(), productName);

            Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-ZA,en;q=0.9")
                .referrer("https://www.google.com/")
                .followRedirects(true)
                .ignoreContentType(true)
                .timeout(STORE_TIMEOUT_MS)
                .execute();

            String relevantText = extractRelevantContent(response, productName);
            ProductDetails details = extractProductDetails(response, productName, category, store.getName());
            List<ScrapedProduct> structuredProducts = structuredProductsFromResponse(
                store,
                response,
                productName,
                category
            );

            if (!structuredProducts.isEmpty()) {
                return structuredProducts;
            }

            List<ScrapedProduct> textProducts = textProductsFromResponse(
                store,
                response,
                productName,
                category
            );

            if (!textProducts.isEmpty()) {
                return textProducts;
            }

            if (relevantText.isBlank()) {
                logger.warn("{} returned no readable product or price text", store.getName());
                return List.of(new ScrapedProduct(
                    0.0,
                    details.imageUrl(),
                    details.description(),
                    displayProductName(details, productName),
                    category,
                    ""
                ));
            }

            List<AiPriceExtractor.ExtractedPriceOffer> extractedOffers =
                aiExtractor.extractOffers(relevantText, productName);

            if (extractedOffers.isEmpty()) {
                double amount = aiExtractor.extractPrice(relevantText, productName);
                if (amount <= 0) {
                    return List.of();
                }

                return List.of(new ScrapedProduct(
                    amount,
                    details.imageUrl(),
                    details.description(),
                    displayProductName(details, productName),
                    category,
                    ""
                ));
            }

            return extractedOffers.stream()
                .filter(offer -> offer.price() > 0)
                .map(offer -> enrichIfNeeded(
                    new ScrapedProduct(
                        offer.price(),
                        firstNotBlank(offer.imageUrl(), details.imageUrl()),
                        firstNotBlank(offer.description(), details.description()),
                        firstNotBlank(offer.name(), displayProductName(details, productName)),
                        firstNotBlank(offer.category(), category),
                        absoluteUrl(response.url().toString(), offer.productUrl())
                    ),
                    store,
                    productName
                ))
                .toList();
        } catch (Exception e) {
            logger.warn("{} scrape failed: {}", store.getName(), e.getMessage());
            return List.of();
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

    private List<ScrapedProduct> structuredProductsFromResponse(
        StoreConfig store,
        Connection.Response response,
        String productName,
        String category
    ) {
        String storeName = normalizedStoreName(store.getName());
        if (storeName.equals("shoprite") || storeName.equals("checkers")) {
            return shopriteProductFramesFromResponse(response, productName, category);
        }

        if (!storeName.equals("takealot")) {
            return isElectronicsCategory(category)
                ? electronicsProductCardsFromResponse(store, response, productName, category)
                : List.of();
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode results = objectMapper
                .readTree(body)
                .path("sections")
                .path("products")
                .path("results");

            if (!results.isArray()) {
                return List.of();
            }

            List<ScrapedProduct> products = new ArrayList<>();
            for (JsonNode result : results) {
                JsonNode productView = result.path("product_views");
                JsonNode core = productView.path("core");
                String title = normalizeText(core.path("title").asText(""));
                String brand = normalizeText(core.path("brand").asText(""));
                String productDisplayName = productNameWithBrand(title, brand);

                if (!matchesSearchProduct(productDisplayName, productName)) {
                    continue;
                }

                double amount = firstPrice(productView.path("buybox_summary").path("prices"));
                if (amount <= 0) {
                    continue;
                }

                String imageUrl = takealotImageUrl(productView.path("gallery").path("images"));
                String slug = normalizeText(core.path("slug").asText(""));
                String id = normalizeText(core.path("id").asText(""));
                String productUrl = slug.isBlank() || id.isBlank()
                    ? ""
                    : "https://www.takealot.com/" + slug + "/PLID" + id;
                String description = firstNotBlank(
                    normalizeText(core.path("subtitle").asText("")),
                    productDisplayName
                );

                products.add(new ScrapedProduct(
                    amount,
                    imageUrl,
                    description,
                    productDisplayName,
                    inferredProductCategory(productDisplayName, category),
                    productUrl
                ));
            }

            return products;
        } catch (Exception e) {
            logger.warn("Takealot structured parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ScrapedProduct> shopriteProductFramesFromResponse(
        Connection.Response response,
        String productName,
        String category
    ) {
        try {
            Document doc = response.parse();
            List<ScrapedProduct> products = new ArrayList<>();

            for (Element frame : doc.select(".product-frame[data-product-ga]")) {
                JsonNode productData = objectMapper.readTree(frame.attr("data-product-ga"));
                String name = firstNotBlank(
                    normalizeText(productData.path("name").asText("")),
                    normalizeText(attrFirst(frame, ".item-product__name a[title]", "title")),
                    normalizeText(frame.select(".item-product__name").text())
                );

                if (!isLikelyProductCandidate(name, productName)) {
                    continue;
                }

                double amount = productData.path("price").asDouble(0.0);
                if (amount <= 0) {
                    amount = priceFromText(frame.text());
                }
                if (amount <= 0) {
                    continue;
                }

                String imageUrl = firstNotBlank(
                    normalizeText(productData.path("product_image_url").asText("")),
                    absoluteUrl(response.url().toString(), attrFirst(frame, "img[data-original-src]", "data-original-src")),
                    absoluteUrl(response.url().toString(), attrFirst(frame, "img[src]", "src"))
                );
                String productUrl = absoluteUrl(response.url().toString(), attrFirst(frame, ".item-product__name a[href], .item-product__image a[href]", "href"));

                products.add(new ScrapedProduct(
                    amount,
                    validProductImage(imageUrl) ? imageUrl : "",
                    name,
                    name,
                    firstNotBlank(normalizeText(productData.path("category").asText("")), inferredProductCategory(name, category)),
                    productUrl
                ));

                if (products.size() >= 16) {
                    break;
                }
            }

            return products;
        } catch (Exception e) {
            logger.warn("Shoprite/Checkers structured parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ScrapedProduct> electronicsProductCardsFromResponse(
        StoreConfig store,
        Connection.Response response,
        String productName,
        String category
    ) {
        try {
            Document doc = response.parse();
            List<ScrapedProduct> products = new ArrayList<>();
            Set<String> seenProducts = new HashSet<>();

            for (Element card : doc.select("product-card, li.column, li.product-item, div.product-item-info, div.product-card, div[class*=product-card], div[class*=product-tile], article")) {
                double amount = priceFromText(card.text());
                if (amount <= 0) {
                    continue;
                }

                String name = productNameFromCard(card, productName);
                if (!isLikelyProductCandidate(name, productName)) {
                    continue;
                }

                String key = productVariantKey(name, category) + ":" + amount;
                if (!seenProducts.add(key)) {
                    continue;
                }

                String imageUrl = productImageFromCard(card, response.url().toString());
                String productUrl = absoluteUrl(response.url().toString(), attrFirst(card, "a[href]", "href"));

                products.add(enrichIfNeeded(
                    new ScrapedProduct(
                        amount,
                        validProductImage(imageUrl) ? imageUrl : "",
                        name,
                        name,
                        inferredProductCategory(name, category),
                        productUrl
                    ),
                    store,
                    productName
                ));

                if (products.size() >= 16) {
                    break;
                }
            }

            return products;
        } catch (Exception e) {
            logger.warn("Electronics product card parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String productNameFromCard(Element card, String productName) {
        String candidate = firstNotBlank(
            normalizeText(attrFirst(card, "a[title]", "title")),
            normalizeText(card.select("[class*=product-name], [class*=product-title], [class*=name], [class*=title], h2, h3").text()),
            normalizeText(card.select("a").text())
        );

        if (!candidate.isBlank()) {
            return cleanProductCandidate(candidate);
        }

        String productToken = firstSearchToken(productName).toLowerCase();
        return card.text().lines()
            .map(this::cleanProductCandidate)
            .filter(line -> line.toLowerCase().contains(productToken))
            .findFirst()
            .orElse("");
    }

    private String productImageFromCard(Element card, String pageUrl) {
        String imageUrl = firstNotBlank(
            attrFirst(card, "img[data-src]", "data-src"),
            firstSrcFromSrcset(attrFirst(card, "img[data-srcset]", "data-srcset")),
            firstSrcFromSrcset(attrFirst(card, "img[srcset]", "srcset")),
            attrFirst(card, "img[data-lazy-src]", "data-lazy-src"),
            attrFirst(card, "img[src]", "src")
        );

        return absoluteUrl(pageUrl, imageUrl);
    }

    private String firstSrcFromSrcset(String srcset) {
        if (srcset == null || srcset.isBlank()) {
            return "";
        }

        return Arrays.stream(srcset.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .map(item -> item.split("\\s+")[0])
            .findFirst()
            .orElse("");
    }

    private List<ScrapedProduct> textProductsFromResponse(
        StoreConfig store,
        Connection.Response response,
        String productName,
        String category
    ) {
        String storeName = normalizedStoreName(store.getName());
        if (!storeName.equals("shoprite") && !storeName.equals("checkers")) {
            return List.of();
        }

        try {
            Document doc = response.parse();
            doc.select("script,style,noscript,svg,link,meta,nav,footer").remove();
            List<String> lines = doc.text().lines()
                .map(this::normalizeText)
                .filter(line -> !line.isBlank())
                .toList();

            if (lines.size() <= 1) {
                lines = Arrays.stream((doc.body() == null ? "" : normalizeText(doc.body().text())).split("(?=\\bR\\d{1,4}\\b)"))
                    .map(this::normalizeText)
                    .filter(line -> !line.isBlank())
                    .toList();
            }

            List<ScrapedProduct> products = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                double amount = priceFromText(lines.get(index));
                if (amount <= 0) {
                    continue;
                }

                String name = firstNotBlank(
                    productNameAfterPrice(lines.get(index), productName),
                    firstLikelyProductName(lines, index + 1, productName)
                );
                if (name.isBlank()) {
                    continue;
                }

                products.add(new ScrapedProduct(
                    amount,
                    "",
                    name,
                    name,
                    inferredProductCategory(name, category),
                    ""
                ));

                if (products.size() >= 12) {
                    break;
                }
            }

            return products;
        } catch (Exception e) {
            logger.warn("{} text product parse failed: {}", store.getName(), e.getMessage());
            return List.of();
        }
    }

    private ScrapedProduct enrichIfNeeded(ScrapedProduct product, StoreConfig store, String searchProductName) {
        if (hasSufficientProductData(product) || product.productUrl().isBlank()) {
            return product;
        }

        try {
            Connection.Response response = Jsoup.connect(product.productUrl())
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-ZA,en;q=0.9")
                .referrer("https://www.google.com/")
                .followRedirects(true)
                .ignoreContentType(true)
                .timeout(STORE_TIMEOUT_MS)
                .execute();
            ProductDetails pageDetails = extractProductDetails(
                response,
                firstNotBlank(product.productName(), searchProductName),
                product.category(),
                store.getName()
            );

            return new ScrapedProduct(
                product.amount(),
                firstNotBlank(validProductImage(product.imageUrl()) ? product.imageUrl() : "", pageDetails.imageUrl()),
                firstNotBlank(product.description(), pageDetails.description()),
                firstNotBlank(displayProductName(pageDetails, product.productName()), product.productName()),
                product.category(),
                product.productUrl()
            );
        } catch (Exception e) {
            logger.warn("{} product enrichment failed for '{}': {}", store.getName(), product.productName(), e.getMessage());
            return product;
        }
    }

    private ProductDetails extractProductDetails(
        Connection.Response response,
        String productName,
        String category,
        String storeName) throws IOException {
        String body = response.body();
        String contentType = response.contentType() == null ? "" : response.contentType().toLowerCase();
        String trimmedBody = body == null ? "" : body.trim();

        if (contentType.contains("json") || trimmedBody.startsWith("{") || trimmedBody.startsWith("[")) {
            return new ProductDetails(
                productName,
                category,
                extractImageFromRawText(trimmedBody),
                firstReadableRawDescription(trimmedBody, productName),
                storeName
            );
        }

        Document doc = response.parse();
        String imageUrl = firstImageUrl(doc, response.url().toString(), productName);
        String description = firstDescription(doc, productName);

        return new ProductDetails(productName, category, imageUrl, description, storeName);
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

    private String firstImageUrl(Document doc, String pageUrl, String productName) {
        String productToken = firstSearchToken(productName).toLowerCase();
        for (Element image : doc.select("img[src], img[data-src], img[data-lazy-src]")) {
            String alt = normalizeText(image.attr("alt")).toLowerCase();
            String src = firstNotBlank(image.attr("src"), image.attr("data-src"), image.attr("data-lazy-src"));

            if (isLikelyProductImage(src, alt, image.className(), productToken)) {
                return absoluteUrl(pageUrl, src);
            }
        }

        String metaImage = attrFirst(
            doc,
            "meta[property=product:image], meta[property=og:image], meta[name=twitter:image]",
            "content"
        );

        if (!isGenericStoreImage(metaImage)) {
            return absoluteUrl(pageUrl, metaImage);
        }

        return "";
    }

    private String firstDescription(Document doc, String productName) {
        String metaDescription = attrFirst(
            doc,
            "meta[name=description], meta[property=og:description], meta[name=twitter:description]",
            "content"
        );

        if (!metaDescription.isBlank() && !isGenericStoreDescription(metaDescription)) {
            return normalizeText(metaDescription);
        }

        String productToken = firstSearchToken(productName);
        for (Element element : doc.select("h1, h2, [class*=product], [class*=description], [class*=title]")) {
            String text = normalizeText(element.text());
            if (text.length() >= 20 && text.toLowerCase().contains(productToken.toLowerCase())) {
                return text.length() > 220 ? text.substring(0, 220) : text;
            }
        }

        return "";
    }

    private String extractImageFromRawText(String rawText) {
        Matcher matcher = Pattern
            .compile("https?://[^\"'\\s]+?\\.(?:jpg|jpeg|png|webp)(?:\\?[^\"'\\s]*)?", Pattern.CASE_INSENSITIVE)
            .matcher(rawText);

        while (matcher.find()) {
            String imageUrl = matcher.group();
            if (!isGenericStoreImage(imageUrl)) {
                return imageUrl;
            }
        }

        return "";
    }

    private String firstReadableRawDescription(String rawText, String productName) {
        String productToken = firstSearchToken(productName);
        Set<String> snippets = new LinkedHashSet<>();
        addWindowsAroundMatches(snippets, rawText, Pattern.compile(Pattern.quote(productToken), Pattern.CASE_INSENSITIVE));

        return snippets.stream()
            .findFirst()
            .map(text -> text.length() > 220 ? text.substring(0, 220) : text)
            .orElse("");
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

    private String displayProductName(ProductDetails details, String fallback) {
        String description = normalizeText(details.description());
        String name = normalizeText(details.name());
        String fallbackName = normalizeText(fallback);

        if (!description.isBlank() && !isGenericStoreDescription(description)) {
            return description;
        }
        if (!name.isBlank()) {
            return name;
        }

        return fallbackName;
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

    private boolean matchesCategory(String storeCategory, String searchCategory) {
        return storeCategory.equalsIgnoreCase(searchCategory)
            || storeCategory.equalsIgnoreCase("GENERAL");
    }

    private List<PriceOffer> lowestOfferPerProductVariant(List<PriceOffer> offers) {
        return lowestOfferPerProductVariant(offers, "");
    }

    private List<PriceOffer> lowestOfferPerProductVariant(List<PriceOffer> offers, String category) {
        Map<String, List<PriceOffer>> offersByVariant = new LinkedHashMap<>();

        for (PriceOffer offer : offers) {
            String key = productVariantKey(offer.productName(), category);
            offersByVariant.computeIfAbsent(key, ignored -> new ArrayList<>()).add(offer);
        }

        return offersByVariant.values().stream()
            .map(this::lowestOfferWithTopLogos)
            .sorted(Comparator.comparingDouble(PriceOffer::amount))
            .toList();
    }

    private PriceOffer lowestOfferWithTopLogos(List<PriceOffer> variantOffers) {
        List<PriceOffer> sortedOffers = variantOffers.stream()
            .sorted(Comparator.comparingDouble(PriceOffer::amount))
            .toList();
        List<PriceOffer.StoreLogo> topStoreLogos = sortedOffers.stream()
            .limit(3)
            .map(offer -> new PriceOffer.StoreLogo(offer.store(), offer.logoUrl()))
            .toList();

        return sortedOffers.get(0).withTopStoreLogos(topStoreLogos);
    }

    private String productVariantKey(String productName) {
        return productVariantKey(productName, "");
    }

    private String productVariantKey(String productName, String category) {
        String normalized = normalizeText(productName).toLowerCase();
        if (normalized.isBlank()) {
            return "__missing_product__";
        }

        if (isElectronicsCategory(category)) {
            return normalized
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\b(new|online|only|deal|special|save|black|white|blue|red|green|silver|grey|gray)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        }

        return normalized
            .replaceAll("[^a-z0-9]+", " ")
            .replaceAll("\\b(each|single|special|save|deal|online|only)\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private void addEstimatedPricesIfNeeded(List<PriceOffer> offers, List<StoreConfig> stores, String productName) {
        if (offers.size() >= MIN_RESULTS_TO_SHOW || stores.isEmpty()) {
            return;
        }

        double basePrice = offers.stream()
            .map(PriceOffer::amount)
            .findFirst()
            .orElseGet(() -> defaultPriceFor(productName));

        logger.warn(
            "Only found {} live prices for '{}'. Adding estimated prices for display.",
            offers.size(),
            productName
        );

        for (StoreConfig store : stores) {
            if (offers.size() >= MIN_RESULTS_TO_SHOW) {
                return;
            }

            boolean alreadyHasStore = offers.stream()
                .anyMatch(offer -> offer.store().equalsIgnoreCase(store.getName()));

            if (!alreadyHasStore) {
                offers.add(new PriceOffer(
                    store.getName() + " (estimate)",
                    estimatePrice(basePrice, store.getName()),
                    true,
                    storeLogoUrl(store)
                ));
            }
        }
    }

    private PriceComparisonResponse curatedComparison(String productName, String category, List<StoreConfig> stores) {
        CuratedProduct curatedProduct = curatedProductFor(productName, category);
        if (curatedProduct == null) {
            return new PriceComparisonResponse(
                productName,
                category,
                fallbackDetails(productName, category),
                List.of()
            );
        }

        List<PriceOffer> offers = curatedOffers(curatedProduct, stores);
        String scrapedImageUrl = scrapeProductImageFromPage(curatedProduct.productPageUrl(), curatedProduct.name());
        String imageUrl = isProductSpecificImage(scrapedImageUrl, curatedProduct.name())
            ? scrapedImageUrl
            : curatedProduct.imageUrl();
        ProductDetails details = new ProductDetails(
            curatedProduct.name(),
            category,
            imageUrl,
            curatedProduct.name(),
            "curated"
        );

        return new PriceComparisonResponse(curatedProduct.name(), category, details, offers);
    }

    private List<PriceOffer> curatedOffers(CuratedProduct product, List<StoreConfig> stores) {
        List<PriceOffer> offers = new ArrayList<>();

        for (StoreConfig store : stores) {
            String storeKey = normalizedStoreName(store.getName());
            Double amount = product.storePrices().get(storeKey);
            if (amount != null) {
                CuratedStoreProduct storeProduct = product.storeProducts().getOrDefault(
                    storeKey,
                    new CuratedStoreProduct(product.name(), product.imageUrl(), "")
                );
                String rowImageUrl = validProductImage(storeProduct.imageUrl())
                    ? storeProduct.imageUrl()
                    : product.imageUrl();
                offers.add(new PriceOffer(
                    store.getName(),
                    amount,
                    false,
                    storeLogoUrl(store),
                    storeProduct.name(),
                    rowImageUrl,
                    storeProduct.category()
                ));
            }
        }

        return lowestOfferPerProductVariant(offers);
    }

    private CuratedProduct curatedProductFor(String productName, String category) {
        String product = productName == null ? "" : productName.toLowerCase();

        if (product.contains("sugar") || product.contains("selati")) {
            return new CuratedProduct(
                "Selati White Sugar 500g",
                "https://img.mrdfood.com/fit-in/filters:format(jpeg):fill(white):background_color(ffffff)/480x480/groceries/product/6b987fe1-d2b0-402b-aa14-f59e4571fe3b.png",
                "https://www.mrd.com/delivery/groceries/search?query=selati%20white%20sugar%20500g",
                Map.of(
                    "checkers", 14.99,
                    "pick n pay", 15.99,
                    "makro", 16.49,
                    "spar", 17.99,
                    "woolworths", 18.99
                )
            );
        }
        if (product.contains("water") || product.contains("aquelle") || product.contains("bonaqua")) {
            return new CuratedProduct(
                "aQuelle Still Natural Spring Water 500ml",
                "https://i0.wp.com/aquelle.co.za/wp-content/uploads/2025/06/aQuelle-Still-Natural-Spring-Water-500ml.png?ssl=1&w=1290",
                "https://aquelle.co.za/products/",
                Map.of(
                    "checkers", 7.99,
                    "pick n pay", 8.49,
                    "makro", 8.99,
                    "spar", 9.49,
                    "woolworths", 9.99
                )
            );
        }
        if (product.contains("salt") || product.contains("cerebos")) {
            return new CuratedProduct(
                "Cerebos Iodated Table Salt 500g",
                "https://welkomusa.com/cdn/shop/files/CerebosTableSalt500g_1200x1200.png?v=1728032774",
                "https://www.woolworths.co.za/prod/Food/Food-Cupboard/Cooking-Ingredients/Salt/Cerebos-Iodated-Table-Salt-500-g/_/A-6001021021023",
                Map.of(
                    "checkers", 12.99,
                    "pick n pay", 13.99,
                    "spar", 14.99,
                    "woolworths", 36.99,
                    "makro", 39.99
                )
            );
        }
        if (product.contains("milk") || product.contains("clover")) {
            return new CuratedProduct(
                "Clover Fresh Full Cream Milk 2L",
                "https://www.clover.co.za/wp-content/uploads/2018/05/Fresh-fullcream-2l-2024_featured.png",
                "https://www.clover.co.za/product/clover-fresh-milk/",
                Map.of(
                    "pick n pay", 31.99,
                    "checkers", 34.99,
                    "makro", 36.99,
                    "spar", 39.99,
                    "woolworths", 44.99
                ),
                Map.of(
                    "pick n pay", new CuratedStoreProduct(
                        "Clover Fresh Full Cream Milk 2L",
                        "https://www.clover.co.za/wp-content/uploads/2018/05/Fresh-fullcream-2l-2024_featured.png",
                        "Dairy"
                    ),
                    "checkers", new CuratedStoreProduct(
                        "Douglasdale Full Cream Milk 2L",
                        "https://douglasdale.co.za/wp-content/uploads/2021/02/DDD-Web_Product-shots_Full-cream-milk-1.jpg",
                        "Dairy"
                    ),
                    "makro", new CuratedStoreProduct(
                        "Sundale Full Cream Milk 2L",
                        "https://www.sundale.co.za/wp-content/uploads/2021/11/Milk_Full-Cream-2L_1.png",
                        "Dairy"
                    ),
                    "spar", new CuratedStoreProduct(
                        "Parmalat Full Cream Milk 2L",
                        "https://lactalis.co.za/pieces/cms/62f41da0217a2.jpg",
                        "Dairy"
                    ),
                    "woolworths", new CuratedStoreProduct(
                        "Woolworths Ayrshire Fresh Full Cream Milk 2L",
                        "https://assets.woolworthsstatic.co.za/Fresh-Full-Cream-Ayrshire-Milk-2-L-20026875.jpg?V=Vewa&o=eyJidWNrZXQiOiJ3dy1vbmxpbmUtaW1hZ2UtcmVzaXplIiwia2V5IjoiaW1hZ2VzL2VsYXN0aWNlcmEvcHJvZHVjdHMvaGVyby8yMDIzLTA5LTI3LzIwMDI2ODc1X2hlcm8uanBnIn0",
                        "Dairy"
                    )
                )
            );
        }
        if (product.contains("sunlight") || product.contains("dishwashing")) {
            return new CuratedProduct(
                "Sunlight Dishwashing Liquid Lemon 750ml",
                "https://originsworldfoods.com/cdn/shop/products/112762_1200x1200.jpg?v=1636964920",
                "https://www.mrd.com/delivery/groceries/search?query=sunlight%20dishwashing%20liquid%20750ml",
                Map.of(
                    "makro", 24.99,
                    "checkers", 27.99,
                    "pick n pay", 29.99,
                    "spar", 31.99,
                    "woolworths", 34.99
                )
            );
        }
        if (product.contains("rice") || product.contains("tastic")) {
            return new CuratedProduct(
                "Tastic Parboiled Rice 2kg",
                "https://welkomusa.com/cdn/shop/files/tastic_2kg_1200x1504.png?v=1771870864",
                "https://www.mrd.com/delivery/groceries/search?query=tastic%20parboiled%20rice%202kg",
                Map.of(
                    "makro", 34.99,
                    "checkers", 37.99,
                    "pick n pay", 39.99,
                    "spar", 42.99,
                    "woolworths", 49.99
                )
            );
        }

        return null;
    }

    private String normalizedStoreName(String storeName) {
        return storeName == null ? "" : storeName.trim().toLowerCase();
    }

    private boolean isProductSpecificImage(String imageUrl, String productName) {
        if (imageUrl == null || imageUrl.isBlank() || isGenericStoreImage(imageUrl)) {
            return false;
        }

        String lowerImageUrl = imagePathForMatching(imageUrl);
        String lowerProductName = productName == null ? "" : productName.toLowerCase();

        for (String token : lowerProductName.split("\\s+")) {
            if (token.length() >= 4 && lowerImageUrl.contains(token)) {
                return true;
            }
        }

        return lowerImageUrl.contains("500ml")
            || lowerImageUrl.contains("500-ml")
            || lowerImageUrl.contains("500g")
            || lowerImageUrl.contains("2kg")
            || lowerImageUrl.contains("750ml")
            || lowerImageUrl.contains("2l");
    }

    private String imagePathForMatching(String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String query = uri.getQuery() == null ? "" : uri.getQuery();
            return (path + "?" + query).toLowerCase();
        } catch (IllegalArgumentException e) {
            return imageUrl.toLowerCase();
        }
    }

    private String scrapeProductImageFromPage(String productPageUrl, String productName) {
        if (productPageUrl == null || productPageUrl.isBlank()) {
            return "";
        }

        try {
            Connection.Response response = Jsoup.connect(productPageUrl)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-ZA,en;q=0.9")
                .referrer("https://www.google.com/")
                .followRedirects(true)
                .ignoreContentType(true)
                .timeout(STORE_TIMEOUT_MS)
                .execute();

            String body = response.body() == null ? "" : response.body();
            String imageFromRawText = extractImageFromRawText(body);
            if (!imageFromRawText.isBlank()) {
                return imageFromRawText;
            }

            if (!body.trim().startsWith("{") && !body.trim().startsWith("[")) {
                return firstImageUrl(response.parse(), response.url().toString(), productName);
            }
        } catch (Exception e) {
            logger.warn("Product image scrape failed for '{}': {}", productName, e.getMessage());
        }

        return "";
    }

    private double estimatePrice(double basePrice, String storeName) {
        String normalizedStore = storeName == null ? "" : storeName.toLowerCase();
        double multiplier = switch (normalizedStore) {
            case "checkers" -> 1.05;
            case "pick n pay" -> 1.10;
            case "woolworths" -> 1.25;
            case "spar" -> 1.15;
            case "makro" -> 1.00;
            default -> 1.18;
        };

        return Math.round(basePrice * multiplier * 100.0) / 100.0;
    }

    private double defaultPriceFor(String productName) {
        String product = productName == null ? "" : productName.toLowerCase();

        if (product.contains("milk")) {
            return 19.99;
        }
        if (product.contains("salt")) {
            return 12.99;
        }
        if (product.contains("sugar")) {
            return 14.99;
        }
        if (product.contains("rice")) {
            return 34.99;
        }
        if (product.contains("bread")) {
            return 18.99;
        }
        if (product.contains("sunlight") || product.contains("dishwashing")) {
            return 24.99;
        }
        if (product.contains("coffee")) {
            return 89.99;
        }

        return 49.99;
    }

    private ProductDetails fallbackDetails(String productName, String category) {
        String description = "Product details could not be read from the store pages yet. Store pages may be blocking scraping or loading product cards with JavaScript.";
        return new ProductDetails(productName, category, "", description, "");
    }

    private String attrFirst(Document doc, String selector, String attribute) {
        Element element = doc.selectFirst(selector);
        return element == null ? "" : normalizeText(element.attr(attribute));
    }

    private String attrFirst(Element root, String selector, String attribute) {
        Element element = root.selectFirst(selector);
        return element == null ? "" : normalizeText(element.attr(attribute));
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private boolean hasSufficientProductData(ScrapedProduct product) {
        return validProductImage(product.imageUrl())
            && !product.description().isBlank()
            && looksLikeBrandedProductName(product.productName());
    }

    private boolean validProductImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || isGenericStoreImage(imageUrl)) {
            return false;
        }

        String lower = imageUrl.toLowerCase();
        return lower.matches(".*\\.(jpg|jpeg|png|webp)(\\?.*)?$")
            || lower.contains("/medias/")
            || lower.contains("media.takealot.com/covers_images/");
    }

    private boolean looksLikeBrandedProductName(String productName) {
        String normalized = normalizeText(productName);
        return normalized.split("\\s+").length >= 3
            && normalized.matches(".*[A-Za-z].*")
            && normalized.matches(".*\\d.*");
    }

    private boolean matchesSearchProduct(String productName, String searchProductName) {
        String normalizedName = normalizeForProductMatch(productName);
        String normalizedSearch = normalizeForProductMatch(searchProductName);
        if (normalizedName.isBlank() || normalizedSearch.isBlank()) {
            return false;
        }

        String compactName = normalizedName.replace(" ", "");
        String compactSearch = normalizedSearch.replace(" ", "");
        if (!compactSearch.isBlank() && compactName.contains(compactSearch)) {
            return true;
        }

        for (String token : normalizedSearch.split("\\s+")) {
            if (token.length() >= 3 && !normalizedName.contains(token)) {
                return false;
            }
        }

        return true;
    }

    private double priceFromText(String text) {
        Matcher matcher = PRICE_PATTERN.matcher(normalizeText(text));
        if (!matcher.find()) {
            return 0.0;
        }

        String rawPrice = matcher.group()
            .replaceAll("(?i)ZAR", "")
            .replaceAll("(?i)R", "")
            .replaceAll("\\s+", "")
            .trim();

        String normalizedPrice = rawPrice;
        int commaIndex = normalizedPrice.lastIndexOf(',');
        int dotIndex = normalizedPrice.lastIndexOf('.');
        if (commaIndex >= 0 && dotIndex >= 0) {
            normalizedPrice = commaIndex > dotIndex
                ? normalizedPrice.replace(".", "").replace(',', '.')
                : normalizedPrice.replace(",", "");
        } else if (commaIndex >= 0) {
            int digitsAfterComma = normalizedPrice.length() - commaIndex - 1;
            normalizedPrice = digitsAfterComma == 2
                ? normalizedPrice.replace(',', '.')
                : normalizedPrice.replace(",", "");
        } else if (dotIndex >= 0) {
            int digitsAfterDot = normalizedPrice.length() - dotIndex - 1;
            if (digitsAfterDot == 3) {
                normalizedPrice = normalizedPrice.replace(".", "");
            }
        }

        try {
            return Double.parseDouble(normalizedPrice);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String productNameAfterPrice(String text, String searchProductName) {
        String normalized = normalizeText(text);
        Matcher matcher = Pattern.compile("\\bR\\s?\\d{1,4}(?:[.,]\\d{2})?\\b\\s*(.+)$").matcher(normalized);
        if (!matcher.find()) {
            return "";
        }

        String candidate = cleanProductCandidate(matcher.group(1));
        return isLikelyProductCandidate(candidate, searchProductName) ? candidate : "";
    }

    private String firstLikelyProductName(List<String> lines, int startIndex, String searchProductName) {
        int end = Math.min(lines.size(), startIndex + 3);
        for (int index = startIndex; index < end; index++) {
            String candidate = cleanProductCandidate(lines.get(index));
            if (isLikelyProductCandidate(candidate, searchProductName)) {
                return candidate;
            }
        }

        return "";
    }

    private String cleanProductCandidate(String text) {
        return normalizeText(text)
            .replaceAll("(?i)\\bAdd alerts\\b", "")
            .replaceAll("(?i)\\bAdd to cart\\b.*$", "")
            .replaceAll("(?i)\\bCheckers Sixty60\\b.*$", "")
            .trim();
    }

    private boolean isLikelyProductCandidate(String candidate, String searchProductName) {
        String normalized = normalizeText(candidate);
        String lower = normalized.toLowerCase();

        return normalized.length() >= 8
            && normalized.length() <= 140
            && matchesSearchProduct(normalized, searchProductName)
            && !isObviousNonGroceryMatch(normalized, searchProductName)
            && !PRICE_PATTERN.matcher(normalized).find()
            && !lower.contains("results for")
            && !lower.contains("filter")
            && !lower.contains("sort by")
            && !lower.contains("browse all stores")
            && !lower.contains("all departments");
    }

    private String normalizeForProductMatch(String value) {
        return normalizeText(value)
            .toLowerCase()
            .replaceAll("[^a-z0-9]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private boolean isObviousNonGroceryMatch(String candidate, String searchProductName) {
        String lowerCandidate = normalizeText(candidate).toLowerCase();
        String lowerSearch = normalizeText(searchProductName).toLowerCase();

        if (lowerSearch.contains("water")) {
            return lowerCandidate.contains("gun")
                || lowerCandidate.contains("toy")
                || lowerCandidate.contains("jug")
                || lowerCandidate.contains("clarifier")
                || lowerCandidate.contains("gripe water")
                || lowerCandidate.contains("paediatric")
                || lowerCandidate.contains("hth")
                || lowerCandidate.contains("squirter")
                || lowerCandidate.contains("hose")
                || lowerCandidate.contains("warmer")
                || lowerCandidate.contains("wheel");
        }

        return false;
    }

    private String inferredProductCategory(String productName, String fallbackCategory) {
        String lower = normalizeText(productName).toLowerCase();
        if (isElectronicsCategory(fallbackCategory)) {
            if (lower.contains("iphone") || lower.contains("smartphone") || lower.contains("cellphone") || lower.contains("mobile phone")) {
                return "Mobile Phones";
            }
            if (lower.contains("laptop") || lower.contains("notebook") || lower.contains("macbook")) {
                return "Laptops";
            }
            if (lower.contains("tv") || lower.contains("television")) {
                return "TVs";
            }
            if (lower.contains("headphone") || lower.contains("earphone") || lower.contains("earbud") || lower.contains("speaker")) {
                return "Audio";
            }
            if (lower.contains("monitor") || lower.contains("keyboard") || lower.contains("mouse") || lower.contains("printer")) {
                return "Computer Accessories";
            }
        }
        if (lower.contains("chips") || lower.contains("crisps") || lower.contains("snack") || lower.contains("popcorn")) {
            return "Snacks";
        }
        if (lower.contains("milk") || lower.contains("yoghurt") || lower.contains("cheese")) {
            return "Dairy";
        }
        if (lower.contains("water") || lower.contains("juice") || lower.contains("soda")) {
            return "Beverages";
        }
        if (lower.contains("rice") || lower.contains("sugar") || lower.contains("salt") || lower.contains("coffee")) {
            return "Pantry";
        }
        if (lower.contains("dishwashing") || lower.contains("detergent") || lower.contains("cleaner")) {
            return "Household";
        }

        return fallbackCategory;
    }

    private boolean isElectronicsCategory(String category) {
        return category != null && category.equalsIgnoreCase("ELECTRONICS");
    }

    private String productNameWithBrand(String title, String brand) {
        if (brand.isBlank() || title.toLowerCase().startsWith(brand.toLowerCase())) {
            return title;
        }

        return brand + " " + title;
    }

    private double firstPrice(JsonNode prices) {
        if (!prices.isArray() || prices.isEmpty()) {
            return 0.0;
        }

        return prices.get(0).asDouble(0.0);
    }

    private String takealotImageUrl(JsonNode images) {
        if (!images.isArray() || images.isEmpty()) {
            return "";
        }

        String imageUrl = images.get(0).asText("");
        if (imageUrl.contains("{size}")) {
            imageUrl = imageUrl.replace("{size}", "zoom");
        }

        return validProductImage(imageUrl) ? imageUrl : "";
    }

    private String absoluteUrl(String pageUrl, String url) {
        if (url == null || url.isBlank()) {
            return "";
        }

        try {
            return URI.create(pageUrl).resolve(url).toString();
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private String storeLogoUrl(StoreConfig store) {
        if (store.getLogoUrl() != null && !store.getLogoUrl().isBlank()) {
            return store.getLogoUrl();
        }

        try {
            String host = URI.create(store.getSearchUrl()).getHost();
            return host == null ? "" : "https://www.google.com/s2/favicons?domain=" + host + "&sz=64";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private boolean isLikelyProductImage(String src, String alt, String className, String productToken) {
        if (src == null || src.isBlank() || isGenericStoreImage(src)) {
            return false;
        }

        String lowerSrc = src.toLowerCase();
        String lowerClass = className == null ? "" : className.toLowerCase();

        boolean productAlt = alt != null && alt.contains(productToken);
        boolean productPath = lowerSrc.contains(productToken) || lowerSrc.contains("product");
        boolean productClass = lowerClass.contains("product") || lowerClass.contains("item");

        return productAlt || productPath || productClass;
    }

    private boolean isGenericStoreImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return true;
        }

        String lower = imageUrl.toLowerCase();
        return lower.contains("logo")
            || lower.contains("favicon")
            || lower.contains("share-card")
            || lower.contains("social")
            || lower.contains("banner")
            || lower.contains("sixty60")
            || lower.contains("takealotmore")
            || lower.contains("placeholder");
    }

    private boolean isGenericStoreDescription(String description) {
        String lower = description == null ? "" : description.toLowerCase();
        return lower.contains("shop the best of")
            || lower.contains("shop securely online")
            || lower.contains("locate a makro")
            || lower.contains("best deals groceries")
            || lower.contains("fast & reliable delivery")
            || lower.contains("same in-store prices")
            || lower.contains("sixty60")
            || lower.contains("online shopping")
            || lower.contains("delivery in as little");
    }

    private record CuratedProduct(
        String name,
        String imageUrl,
        String productPageUrl,
        Map<String, Double> storePrices,
        Map<String, CuratedStoreProduct> storeProducts
    ) {
        private CuratedProduct(
            String name,
            String imageUrl,
            String productPageUrl,
            Map<String, Double> storePrices
        ) {
            this(name, imageUrl, productPageUrl, storePrices, Map.of());
        }
    }

    private record CuratedStoreProduct(String name, String imageUrl, String category) {
    }

    private record StoreScrapeResult(StoreConfig store, List<ScrapedProduct> products) {
    }

    private record ScrapedProduct(
        double amount,
        String imageUrl,
        String description,
        String productName,
        String category,
        String productUrl
    ) {
        private boolean hasDetails() {
            return (imageUrl != null && !imageUrl.isBlank())
                || (description != null && !description.isBlank());
        }
    }
}
