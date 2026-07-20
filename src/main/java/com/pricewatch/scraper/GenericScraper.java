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
import org.jsoup.HttpStatusException;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GenericScraper {
    private static final Logger logger = LoggerFactory.getLogger(GenericScraper.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?i)(?:R|ZAR)\\s?\\d{1,4}(?:[\\s,]\\d{3})*(?:[.,]\\d{2})?");
    // Two timeout tiers. The fast tier bounds a user-facing (never-seen) search
    // so it stays snappy. The thorough tier runs only in the background, where
    // nobody is waiting, and is generous enough for slow stores to finish and be
    // stored for next time. Jsoup spends half its timeout on connect and half on
    // read, so the thorough value is double the scrape budget: the slowest store
    // (Loot sometimes tarpits non-browser clients for 30s+) gets the whole
    // budget as read time and the budget itself decides the cutoff.
    private static final int STORE_TIMEOUT_FAST_MS = 8_000;
    private static final long SCRAPE_BUDGET_FAST_MS = 10_000;
    private static final int STORE_TIMEOUT_THOROUGH_MS = 90_000;
    private static final long SCRAPE_BUDGET_THOROUGH_MS = 45_000;
    private static final int MAX_PRODUCTS_PER_STORE = 40;
    private static final int BREAKER_FAILURE_THRESHOLD = 3;
    private static final Duration BREAKER_COOLDOWN = Duration.ofMinutes(10);
    // Within one search's results (already filtered to the search term), a
    // matching pack size plus moderate name overlap is enough to call two
    // offers the same product; grocery titles vary too much for a strict bar.
    private static final double VARIANT_MATCH_THRESHOLD = 0.55;
    private static final Pattern PACK_SIZE_PATTERN =
        Pattern.compile("\\b(\\d+(?:[.,]\\d+)?)\\s*(ml|l|litre|liter|kg|g|gram)s?\\b");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService scraperExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpClient http2Client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build();
    private final Map<String, StoreHealth> storeHealthByName = new ConcurrentHashMap<>();
    private List<StoreConfig> knownStores = new ArrayList<>();

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

    public PriceComparisonResponse scrapeProductComparison(String productName, String category) {
        return scrapeProductComparison(productName, category, false);
    }

    public PriceComparisonResponse scrapeProductComparison(String productName, String category, boolean thorough) {
        String normalizedCategory = category == null || category.isBlank() ? "GROCERY" : category;
        List<PriceOffer> offers = new ArrayList<>();
        ProductDetails details = null;

        Set<String> seen = new HashSet<>();
        List<StoreConfig> allStores = new ArrayList<>();
        for (StoreConfig store : knownStores) {
            if (store.getCategory() != null && matchesCategory(store.getCategory(), normalizedCategory)) {
                addUniqueStore(seen, allStores, store);
            }
        }

        // Every store with a parser is searched regardless of its category tag so
        // cross-store comparison sees the whole market (e.g. Clicks and Dis-Chem
        // both sell bottled water even though they are tagged HEALTH). Stores
        // without a parser (JS-rendered or bot-blocked sites) can't be scraped;
        // they still appear in the curated fallback below.
        Set<String> seenScrapable = new HashSet<>();
        List<StoreConfig> scrapableStores = new ArrayList<>();
        for (StoreConfig store : knownStores) {
            if (store.getParser() != null) {
                addUniqueStore(seenScrapable, scrapableStores, store);
            }
        }

        List<StoreScrapeResult> scrapeResults = scrapeStores(scrapableStores, productName, normalizedCategory, thorough);

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

        // Stores without a parser (bot-walled or app-only sites: Pick n Pay,
        // Spar, Makro) can never contribute live offers, so when the search
        // matches a curated product their curated prices join the live results
        // before grouping; grouping then merges them into matching variants.
        offers.addAll(curatedOffersForUnscrapableStores(productName, normalizedCategory, allStores));

        offers = groupOffersAcrossStores(offers);

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

    private List<StoreScrapeResult> scrapeStores(List<StoreConfig> stores, String productName, String category, boolean thorough) {
        long budgetMs = thorough ? SCRAPE_BUDGET_THOROUGH_MS : SCRAPE_BUDGET_FAST_MS;

        // Materialize all futures before joining so stores scrape concurrently;
        // joining inside the same stream pipeline would run them one at a time.
        List<CompletableFuture<StoreScrapeResult>> futures = stores.stream()
            .filter(this::breakerAllows)
            .map(store -> CompletableFuture.supplyAsync(
                () -> new StoreScrapeResult(store, scrapeStore(store, productName, category, thorough)),
                scraperExecutor
            ))
            .toList();

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .get(budgetMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warn("Scrape budget of {}ms exhausted; returning stores that finished in time", budgetMs);
        }

        return futures.stream()
            .filter(future -> future.isDone() && !future.isCompletedExceptionally())
            .map(CompletableFuture::join)
            .toList();
    }

    private List<ScrapedProduct> scrapeStore(StoreConfig store, String productName, String category, boolean thorough) {
        int timeoutMs = thorough ? STORE_TIMEOUT_THOROUGH_MS : STORE_TIMEOUT_FAST_MS;
        try {
            FetchedPage response = fetchPage(buildSearchUrl(store.getSearchUrl(), productName), timeoutMs, store.getUserAgent());
            List<ScrapedProduct> products = switch (store.getParser().getType()) {
                case "takealot-api" -> takealotProductsFromResponse(response, productName, category);
                case "constructor-api" -> constructorProductsFromResponse(store, response, productName, category);
                case "sixty60-api" -> sixty60ProductsFromResponse(store, response, productName, category);
                case "klevu-api" -> klevuProductsFromResponse(store, response, productName, category);
                case "shoprite-frames" -> shopriteProductsFromResponse(response, productName, category);
                default -> cssProductsFromDocument(store, response.parse(), response.url(), productName, category);
            };

            recordStoreResult(store, !products.isEmpty(), thorough);
            return products;
        } catch (Exception e) {
            logger.warn("{} scrape failed: {}", store.getName(), e.getMessage());
            recordStoreResult(store, false, thorough);
            return List.of();
        }
    }

    // Parses the Constructor.io search API used by some major supermarkets
    // (Woolworths). Its key is public in the site's page source, so we call the
    // same JSON endpoint the browser does — no headless render or bot wall.
    private List<ScrapedProduct> constructorProductsFromResponse(
        StoreConfig store,
        FetchedPage response,
        String productName,
        String category
    ) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode results = objectMapper.readTree(body).path("response").path("results");
            if (!results.isArray()) {
                return List.of();
            }

            String siteBase = storeSiteBaseUrl(store);
            List<ScrapedProduct> products = new ArrayList<>();
            for (JsonNode result : results) {
                String name = normalizeText(result.path("value").asText(""));
                if (!isLikelyProductCandidate(name, productName)) {
                    continue;
                }

                JsonNode data = result.path("data");
                double amount = firstConstructorPrice(data);
                if (amount <= 0) {
                    continue;
                }

                String imageUrl = normalizeText(data.path("image_url").asText(""));
                String productUrl = absoluteUrl(siteBase, normalizeText(data.path("url").asText("")));

                products.add(new ScrapedProduct(
                    amount,
                    validProductImage(imageUrl) ? imageUrl : "",
                    name,
                    name,
                    inferredProductCategory(name, category),
                    productUrl
                ));

                if (products.size() >= MAX_PRODUCTS_PER_STORE) {
                    break;
                }
            }

            return products;
        } catch (Exception e) {
            logger.warn("{} Constructor.io parse failed: {}", store.getName(), e.getMessage());
            return List.of();
        }
    }

    // Parses the Sixty60 catalog API behind the rebuilt Checkers/Shoprite sites.
    // Both webshops are client-rendered Next.js apps now, but they read from
    // this JSON endpoint, which answers without any auth. The storeId baked into
    // the search URL picks which branch's catalog (and prices) we see.
    private List<ScrapedProduct> sixty60ProductsFromResponse(
        StoreConfig store,
        FetchedPage response,
        String productName,
        String category
    ) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode items = objectMapper.readTree(body).path("items");
            if (!items.isArray()) {
                return List.of();
            }

            String siteBase = storeSiteBaseUrl(store);
            List<ScrapedProduct> products = new ArrayList<>();
            for (JsonNode item : items) {
                String name = firstNotBlank(
                    normalizeText(item.path("displayName").asText("")),
                    normalizeText(item.path("name").asText(""))
                );
                if (!isLikelyProductCandidate(name, productName)) {
                    continue;
                }

                // Prices come as integer cents plus a divisor, e.g. 1549 / 100.
                double priceFactor = item.path("priceFactor").asDouble(100.0);
                double amount = priceFactor > 0
                    ? item.path("priceWithoutDecimal").asDouble(0.0) / priceFactor
                    : 0.0;
                if (amount <= 0) {
                    continue;
                }

                String imageId = firstNotBlank(
                    normalizeText(item.path("imageId").asText("")),
                    normalizeText(item.path("imageIds").path(0).asText(""))
                );
                String imageUrl = imageId.isBlank()
                    ? ""
                    : "https://catalog.sixty60.co.za/files/" + imageId;
                String id = normalizeText(item.path("id").asText(""));
                String productUrl = id.isBlank() ? "" : absoluteUrl(siteBase, "product/" + id);
                String description = firstNotBlank(
                    normalizeText(item.path("shortDescription").asText("")),
                    name
                );

                products.add(new ScrapedProduct(
                    amount,
                    validProductImage(imageUrl) ? imageUrl : "",
                    description,
                    name,
                    inferredProductCategory(name, category),
                    productUrl
                ));

                if (products.size() >= MAX_PRODUCTS_PER_STORE) {
                    break;
                }
            }

            return products;
        } catch (Exception e) {
            logger.warn("{} Sixty60 parse failed: {}", store.getName(), e.getMessage());
            return List.of();
        }
    }

    // Parses the Klevu hosted-search API (HiFi Corp). Like Constructor.io, the
    // key ships in the site's page source, so we call the JSON endpoint the
    // storefront itself uses instead of scraping its client-rendered pages.
    private List<ScrapedProduct> klevuProductsFromResponse(
        StoreConfig store,
        FetchedPage response,
        String productName,
        String category
    ) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode results = objectMapper.readTree(body).path("result");
            if (!results.isArray()) {
                return List.of();
            }

            String siteBase = storeSiteBaseUrl(store);
            List<ScrapedProduct> products = new ArrayList<>();
            for (JsonNode result : results) {
                String name = normalizeText(result.path("name").asText(""));
                if (!isLikelyProductCandidate(name, productName)) {
                    continue;
                }

                double amount = firstKlevuPrice(result);
                if (amount <= 0) {
                    continue;
                }

                String imageUrl = firstNotBlank(
                    normalizeText(result.path("imageUrl").asText("")),
                    normalizeText(result.path("image").asText(""))
                );
                String productUrl = absoluteUrl(siteBase, normalizeText(result.path("url").asText("")));

                products.add(new ScrapedProduct(
                    amount,
                    validProductImage(imageUrl) ? imageUrl : "",
                    name,
                    name,
                    inferredProductCategory(name, category),
                    productUrl
                ));

                if (products.size() >= MAX_PRODUCTS_PER_STORE) {
                    break;
                }
            }

            return products;
        } catch (Exception e) {
            logger.warn("{} Klevu parse failed: {}", store.getName(), e.getMessage());
            return List.of();
        }
    }

    // Klevu sends prices as strings ("1899.0"); salePrice is what the shopper
    // pays now, with price/startPrice as fallbacks when it is absent.
    private double firstKlevuPrice(JsonNode result) {
        for (String field : new String[] {"salePrice", "price", "startPrice"}) {
            try {
                double value = Double.parseDouble(result.path(field).asText("0"));
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the next field
            }
        }
        return 0.0;
    }

    // Constructor.io exposes tiered price fields (p10/p30/p60); p10 is the
    // current selling price. Fall back across tiers in case one is absent.
    private double firstConstructorPrice(JsonNode data) {
        for (String field : new String[] {"p10", "p30", "p60", "price"}) {
            double value = data.path(field).asDouble(0.0);
            if (value > 0) {
                return value;
            }
        }
        return 0.0;
    }

    private String storeSiteBaseUrl(StoreConfig store) {
        String base = store.getSiteBaseUrl();
        if (base != null && !base.isBlank()) {
            return base;
        }

        try {
            URI uri = URI.create(store.getSearchUrl());
            return uri.getScheme() + "://" + uri.getHost();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private FetchedPage fetchPage(String url, int timeoutMs) throws IOException {
        return fetchPage(url, timeoutMs, null);
    }

    private FetchedPage fetchPage(String url, int timeoutMs, String userAgentOverride) throws IOException {
        String userAgent = firstNotBlank(userAgentOverride, USER_AGENT);
        try {
            Connection.Response response = Jsoup.connect(url)
                .userAgent(userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-ZA,en;q=0.9")
                .referrer("https://www.google.com/")
                .followRedirects(true)
                .ignoreContentType(true)
                // Server-rendered search pages can be large (Pick n Pay ~1.6MB);
                // jsoup's 2MB default leaves too little headroom.
                .maxBodySize(5 * 1024 * 1024)
                .timeout(timeoutMs)
                .execute();
            return new FetchedPage(response.url().toString(), response.body());
        } catch (HttpStatusException e) {
            // Some WAFs (Faithful to Nature) fingerprint Jsoup's HTTP/1.1
            // transport and 403 it while serving the same request over
            // HTTP/2; retry those with Java's HTTP/2 client.
            if (e.getStatusCode() != 403) {
                throw e;
            }
            return fetchPageHttp2(url, timeoutMs, userAgent);
        }
    }

    private FetchedPage fetchPageHttp2(String url, int timeoutMs, String userAgent) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-ZA,en;q=0.9")
            .header("Referer", "https://www.google.com/")
            .timeout(Duration.ofMillis(timeoutMs))
            .GET()
            .build();

        try {
            HttpResponse<String> response = http2Client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP error fetching URL. Status=" + response.statusCode() + ", URL=[" + url + "]");
            }
            return new FetchedPage(response.uri().toString(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }

    // A fetched page decoupled from the transport that produced it, so parser
    // code works the same whether Jsoup or the HTTP/2 fallback fetched it.
    private record FetchedPage(String url, String body) {
        private Document parse() {
            return Jsoup.parse(body == null ? "" : body, url);
        }
    }

    // Skips stores that keep failing (bot-blocked, down, or unparseable) for a
    // cooldown period instead of burning their full timeout on every search.
    private boolean breakerAllows(StoreConfig store) {
        StoreHealth health = storeHealthByName.get(storeHealthKey(store));
        if (health == null || health.skipUntil == null || Instant.now().isAfter(health.skipUntil)) {
            return true;
        }

        logger.debug("Skipping {} until {} after repeated scrape failures", store.getName(), health.skipUntil);
        return false;
    }

    private void recordStoreResult(StoreConfig store, boolean success, boolean thorough) {
        StoreHealth health = storeHealthByName.computeIfAbsent(storeHealthKey(store), key -> new StoreHealth());

        if (success) {
            health.consecutiveFailures = 0;
            health.skipUntil = null;
            return;
        }

        // Only the thorough tier judges failures: a store that misses the tight
        // fast-tier window is not necessarily down, and penalising it there would
        // wrongly skip it on the background tier where its longer timeout applies.
        if (!thorough) {
            return;
        }

        health.consecutiveFailures++;
        if (health.consecutiveFailures >= BREAKER_FAILURE_THRESHOLD) {
            health.skipUntil = Instant.now().plus(BREAKER_COOLDOWN);
            logger.info("{} failed {} times in a row; skipping it until {}",
                store.getName(), health.consecutiveFailures, health.skipUntil);
        }
    }

    private String storeHealthKey(StoreConfig store) {
        return normalizedStoreName(store.getName());
    }

    private static final class StoreHealth {
        private volatile int consecutiveFailures;
        private volatile Instant skipUntil;
    }

    private List<ScrapedProduct> takealotProductsFromResponse(
        FetchedPage response,
        String productName,
        String category
    ) {
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

    private List<ScrapedProduct> shopriteProductsFromResponse(
        FetchedPage response,
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
                    absoluteUrl(response.url(), attrFirst(frame, "img[data-original-src]", "data-original-src")),
                    absoluteUrl(response.url(), attrFirst(frame, "img[src]", "src"))
                );
                String productUrl = absoluteUrl(response.url(), attrFirst(frame, ".item-product__name a[href], .item-product__image a[href]", "href"));

                products.add(new ScrapedProduct(
                    amount,
                    validProductImage(imageUrl) ? imageUrl : "",
                    name,
                    name,
                    firstNotBlank(normalizeText(productData.path("category").asText("")), inferredProductCategory(name, category)),
                    productUrl
                ));

                if (products.size() >= MAX_PRODUCTS_PER_STORE) {
                    break;
                }
            }

            if (products.isEmpty()) {
                return textProductsFromDocument(doc, productName, category);
            }

            return products;
        } catch (Exception e) {
            logger.warn("Shoprite/Checkers structured parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    // Reads products off a search page using the store's CSS selectors from
    // stores.json. Name and price selectors are optional: missing name falls
    // back to card heuristics, missing price to a currency regex over card text.
    private List<ScrapedProduct> cssProductsFromDocument(
        StoreConfig store,
        Document doc,
        String pageUrl,
        String productName,
        String category
    ) {
        StoreConfig.ParserConfig parser = store.getParser();
        try {
            List<ScrapedProduct> products = new ArrayList<>();
            Set<String> seenProducts = new HashSet<>();

            for (Element card : doc.select(parser.getCard())) {
                String name = cleanProductCandidate(firstNotBlank(
                    selectorText(card, parser.getName()),
                    productNameFromCard(card, productName)
                ));
                if (!isLikelyProductCandidate(name, productName)) {
                    continue;
                }

                double amount = priceFromSelector(card, parser.getPrice());
                if (amount <= 0) {
                    amount = priceFromText(card.text());
                }
                if (amount <= 0) {
                    continue;
                }

                String key = productVariantKey(name, category) + ":" + amount;
                if (!seenProducts.add(key)) {
                    continue;
                }

                String imageUrl = productImageFromCard(card, pageUrl);
                String productUrl = absoluteUrl(pageUrl, attrFirst(card, "a[href]", "href"));

                products.add(new ScrapedProduct(
                    amount,
                    validProductImage(imageUrl) ? imageUrl : "",
                    name,
                    name,
                    inferredProductCategory(name, category),
                    productUrl
                ));

                if (products.size() >= MAX_PRODUCTS_PER_STORE) {
                    break;
                }
            }

            return products;
        } catch (Exception e) {
            logger.warn("{} selector parse failed: {}", store.getName(), e.getMessage());
            return List.of();
        }
    }

    private String selectorText(Element card, String selector) {
        if (selector == null || selector.isBlank()) {
            return "";
        }

        Element element = card.selectFirst(selector);
        return element == null ? "" : normalizeText(element.text());
    }

    // Price elements vary: visible text ("R 1,299.00"), a content attribute
    // (schema.org itemprop=price), or a value attribute (hidden inputs).
    private double priceFromSelector(Element card, String selector) {
        if (selector == null || selector.isBlank()) {
            return 0.0;
        }

        Element element = card.selectFirst(selector);
        if (element == null) {
            return 0.0;
        }

        double fromText = priceFromText(element.text());
        if (fromText > 0) {
            return fromText;
        }

        for (String attribute : new String[] {"content", "value", "data-price"}) {
            String raw = element.attr(attribute).trim();
            if (!raw.isBlank()) {
                double amount = priceFromText(raw.startsWith("R") ? raw : "R" + raw);
                if (amount > 0) {
                    return amount;
                }
            }
        }

        return 0.0;
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

    // Last-resort parse for Shoprite/Checkers pages whose product frames did not
    // render: walks visible text lines pairing prices with likely product names.
    private List<ScrapedProduct> textProductsFromDocument(
        Document doc,
        String productName,
        String category
    ) {
        try {
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
            logger.warn("Text product parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildSearchUrl(String searchUrl, String productName) {
        // %20 instead of '+' so the query also works inside URL paths
        // (e.g. Pick n Pay's /search/{query}), where '+' is a literal plus.
        String encodedProductName = URLEncoder.encode(productName, StandardCharsets.UTF_8).replace("+", "%20");

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

    private String normalizeText(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String firstSearchToken(String productName) {
        if (productName == null || productName.isBlank()) {
            return "__missing_product_name__";
        }

        return productName.trim().split("\\s+")[0];
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

    // Groups the same product sold by different stores into one offer, tolerant
    // of wording differences ("aQuelle Still Spring Water 500ml" vs "Aquelle
    // Spring Water Still 500ml"): offers merge when their pack size matches and
    // their name tokens overlap enough. The merged offer is the cheapest store's,
    // carrying every store's price so the UI can show "found at N stores".
    private List<PriceOffer> groupOffersAcrossStores(List<PriceOffer> offers) {
        List<VariantCluster> clusters = new ArrayList<>();

        for (PriceOffer offer : offers) {
            List<String> tokens = significantNameTokens(offer.productName());
            String size = packSizeToken(offer.productName());

            VariantCluster match = null;
            for (VariantCluster cluster : clusters) {
                // A missing size on either side is a wildcard: one store stating
                // "12g" and another omitting it must not force two separate cards.
                // When both state a size they must agree, so 250g never merges 500g.
                boolean sizeOk = cluster.size.isEmpty() || size.isEmpty() || cluster.size.equals(size);
                if (sizeOk && tokenOverlap(cluster.tokens, tokens) >= VARIANT_MATCH_THRESHOLD) {
                    match = cluster;
                    break;
                }
            }

            if (match == null) {
                clusters.add(new VariantCluster(tokens, size, new ArrayList<>(List.of(offer))));
            } else {
                match.offers().add(offer);
            }
        }

        return clusters.stream()
            .map(this::clusterToOffer)
            .sorted(Comparator.comparingDouble(PriceOffer::amount))
            .toList();
    }

    private PriceOffer clusterToOffer(VariantCluster cluster) {
        Map<String, PriceOffer> lowestPerStore = new LinkedHashMap<>();
        for (PriceOffer offer : cluster.offers()) {
            lowestPerStore.merge(offer.store(), offer, (a, b) -> a.amount() <= b.amount() ? a : b);
        }

        List<PriceOffer> byPrice = lowestPerStore.values().stream()
            .sorted(Comparator.comparingDouble(PriceOffer::amount))
            .toList();

        List<PriceOffer.StoreOffer> storeOffers = byPrice.stream()
            .map(offer -> new PriceOffer.StoreOffer(offer.store(), offer.amount(), offer.logoUrl()))
            .toList();

        return byPrice.get(0).withStoreOffers(storeOffers);
    }

    private List<String> significantNameTokens(String productName) {
        String normalized = normalizeForProductMatch(productName)
            .replaceAll("\\b(each|single|special|save|deal|online|only|new|the|and|with|for|original|cereal)\\b", " ")
            .replaceAll("\\b\\d+(?:[.,]\\d+)?\\s*(?:ml|l|litre|liter|kg|g|gram)s?\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();

        if (normalized.isBlank()) {
            return List.of("__missing_product__");
        }

        // Single-character leftovers (the "s" from "Kellogg's") only add noise.
        return Arrays.stream(normalized.split("\\s+"))
            .filter(token -> token.length() > 1)
            .distinct()
            .toList();
    }

    private String packSizeToken(String productName) {
        Matcher matcher = PACK_SIZE_PATTERN.matcher(normalizeForProductMatch(productName));
        if (!matcher.find()) {
            return "";
        }

        String quantity = matcher.group(1).replace(',', '.');
        String unit = matcher.group(2).toLowerCase();
        unit = switch (unit) {
            case "litre", "liter" -> "l";
            case "gram", "grams" -> "g";
            default -> unit;
        };

        return quantity + unit;
    }

    // Jaccard overlap with compound-word alignment: adjacent tokens fuse when
    // the other name spells them as one word, so "corn flakes" matches
    // "cornflakes" and "dish washing" matches "dishwashing". Tokens also count
    // as shared when one is a prefix of the other, so a store's "Org" matches
    // another's "Organic" without the abbreviation dragging the score down.
    private double tokenOverlap(List<String> aTokens, List<String> bTokens) {
        Set<String> b = fuseCompoundsAgainst(bTokens, new HashSet<>(aTokens));
        Set<String> a = fuseCompoundsAgainst(aTokens, b);

        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        long shared = a.stream().filter(at -> b.stream().anyMatch(bt -> tokensRelated(at, bt))).count();
        long unionSize = a.size() + b.size() - shared;
        return unionSize == 0 ? 0.0 : (double) shared / unionSize;
    }

    private Set<String> fuseCompoundsAgainst(List<String> tokens, Set<String> other) {
        Set<String> fused = new HashSet<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (index + 1 < tokens.size() && other.contains(tokens.get(index) + tokens.get(index + 1))) {
                fused.add(tokens.get(index) + tokens.get(index + 1));
                index++;
            } else {
                fused.add(tokens.get(index));
            }
        }

        return fused;
    }

    private record VariantCluster(List<String> tokens, String size, List<PriceOffer> offers) {
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

    // Curated offers for just the stores that can never be scraped live, added
    // alongside scraped results so those stores are still represented when the
    // search matches a curated product. Scrapable stores are excluded here —
    // their live results (or live absence) speak for themselves.
    private List<PriceOffer> curatedOffersForUnscrapableStores(
        String productName,
        String category,
        List<StoreConfig> stores
    ) {
        CuratedProduct curatedProduct = curatedProductFor(productName, category);
        if (curatedProduct == null) {
            return List.of();
        }

        List<StoreConfig> unscrapableStores = stores.stream()
            .filter(store -> store.getParser() == null)
            .toList();

        // Ungrouped on purpose: the caller groups these together with the live
        // offers, which merges them into matching product variants.
        return rawCuratedOffers(curatedProduct, unscrapableStores);
    }

    private List<PriceOffer> curatedOffers(CuratedProduct product, List<StoreConfig> stores) {
        return groupOffersAcrossStores(rawCuratedOffers(product, stores));
    }

    private List<PriceOffer> rawCuratedOffers(CuratedProduct product, List<StoreConfig> stores) {
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

        return offers;
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
            FetchedPage response = fetchPage(productPageUrl, STORE_TIMEOUT_FAST_MS);

            String body = response.body() == null ? "" : response.body();
            String imageFromRawText = extractImageFromRawText(body);
            if (!imageFromRawText.isBlank()) {
                return imageFromRawText;
            }

            if (!body.trim().startsWith("{") && !body.trim().startsWith("[")) {
                return firstImageUrl(response.parse(), response.url(), productName);
            }
        } catch (Exception e) {
            logger.warn("Product image scrape failed for '{}': {}", productName, e.getMessage());
        }

        return "";
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

    private boolean validProductImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || isGenericStoreImage(imageUrl)) {
            return false;
        }

        String lower = imageUrl.toLowerCase();
        return lower.matches(".*\\.(jpg|jpeg|png|webp)(\\?.*)?$")
            || lower.contains("/medias/")
            || lower.contains("media.takealot.com/covers_images/")
            || lower.contains("catalog.sixty60.co.za/files/");
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

        List<String> nameTokens = Arrays.asList(normalizedName.split("\\s+"));
        for (String token : normalizedSearch.split("\\s+")) {
            if (token.length() >= 3 && nameTokens.stream().noneMatch(nt -> tokensRelated(nt, token))) {
                return false;
            }
        }

        return true;
    }

    // egg<->eggs, tomato<->tomatoes: a match when one token is a prefix of the
    // other, which fixes plurals/variants without loosening to arbitrary
    // substrings (so "ice" still won't match "price").
    private boolean tokensRelated(String nameToken, String searchToken) {
        if (nameToken.equals(searchToken)) {
            return true;
        }
        String shorter = nameToken.length() <= searchToken.length() ? nameToken : searchToken;
        String longer = nameToken.length() <= searchToken.length() ? searchToken : nameToken;
        return shorter.length() >= 3 && longer.startsWith(shorter);
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
        if (brand.isBlank()) {
            return title;
        }

        // Compare ignoring punctuation so "Kellogg's" is recognised inside
        // "Kelloggs Corn Flakes" and the brand is not prepended twice.
        String compactTitle = title.toLowerCase().replaceAll("[^a-z0-9]", "");
        String compactBrand = brand.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (compactBrand.isBlank() || compactTitle.contains(compactBrand)) {
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
        // The Sixty60 catalog CDN hosts real product photos; the "sixty60"
        // keyword below only guards against the marketing banners that carry
        // the brand name in the file name.
        if (lower.contains("catalog.sixty60.co.za/files/")) {
            return false;
        }

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
            || lower.contains("leading online store")
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
