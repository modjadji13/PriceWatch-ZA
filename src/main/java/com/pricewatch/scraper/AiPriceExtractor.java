package com.pricewatch.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiPriceExtractor {
    private static final Logger logger = LoggerFactory.getLogger(AiPriceExtractor.class);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)");

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.api.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    // discovers stores for a given category
    public List<StoreConfig> discoverStores(String category) {
        if (!isConfigured()) {
            return new ArrayList<>();
        }

        String prompt = """
            List South African online stores that sell %s products.
            Return JSON array only, no explanation, no markdown:
            [{"name": "StoreName", "searchUrl": "https://...", "category": "%s"}]
            Only include stores with working search URLs.
            """.formatted(category, category);

        String response = callGroq(prompt);
        return parseStoreConfigs(response);
    }

    // extracts price from raw HTML using Groq
    public double extractPrice(String html, String productName) {
        if (!isConfigured()) {
            return 0.0;
        }

        String trimmedHtml = html.length() > 3000
            ? html.substring(0, 3000)
            : html;

        String prompt = """
            Extract the price of "%s" from this HTML.
            Return a number only, no currency symbol, no explanation.
            Example: 15.99
            If no price found return 0.
            HTML: %s
            """.formatted(productName, trimmedHtml);

        String response = callGroq(prompt).trim();
        return parsePrice(response);
    }

    // makes the actual Groq API call
    private String callGroq(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
            Map.of("role", "user", "content", prompt)
        ));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<AiResponse> response = restTemplate.postForEntity(
                apiUrl, request, AiResponse.class
            );
            AiResponse aiResponse = response.getBody();
            if (aiResponse == null || aiResponse.getChoices() == null || aiResponse.getChoices().isEmpty()) {
                return "0";
            }

            AiMessage message = aiResponse.getChoices().get(0).getMessage();
            return message == null ? "0" : message.getContent();
        } catch (Exception e) {
            logger.warn("Groq call failed: {}", e.getMessage());
            return "0";
        }
    }

    // parses Groq JSON response into StoreConfig list
    private List<StoreConfig> parseStoreConfigs(String json) {
        try {
            String cleanedJson = extractJsonArray(json);
            return mapper.readValue(cleanedJson,
                mapper.getTypeFactory()
                    .constructCollectionType(List.class, StoreConfig.class)
            );
        } catch (Exception e) {
            logger.warn("Failed to parse store configs: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private boolean isConfigured() {
        boolean configured = apiKey != null
            && !apiKey.isBlank()
            && !apiKey.toLowerCase().startsWith("your")
            && apiUrl != null
            && !apiUrl.isBlank()
            && model != null
            && !model.isBlank();

        if (!configured) {
            logger.warn("Groq is not configured. Set GROQ_API_KEY or groq.api.key.");
        }

        return configured;
    }

    private double parsePrice(String response) {
        Matcher matcher = NUMBER_PATTERN.matcher(response.replace(" ", ""));
        if (!matcher.find()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(matcher.group(1).replace(",", "."));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String extractJsonArray(String response) {
        String trimmed = response.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');

        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }

    public static class AiResponse {
        private List<AiChoice> choices;

        public List<AiChoice> getChoices() {
            return choices;
        }

        public void setChoices(List<AiChoice> choices) {
            this.choices = choices;
        }
    }

    public static class AiChoice {
        private AiMessage message;

        public AiMessage getMessage() {
            return message;
        }

        public void setMessage(AiMessage message) {
            this.message = message;
        }
    }

    public static class AiMessage {
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
