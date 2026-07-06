package com.pricewatch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * The last served comparison for a search term. One row per term+category,
 * overwritten whenever a fresh scrape completes, so searches are answered from
 * the database instantly while scraping happens in the background.
 */
@Entity
@Table(
    name = "search_results",
    uniqueConstraints = @UniqueConstraint(columnNames = {"search_term", "category"})
)
public class SearchResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "search_term", nullable = false)
    private String searchTerm;

    @Column(nullable = false)
    private String category;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "scraped_at", nullable = false)
    private LocalDateTime scrapedAt;

    public SearchResult() {
    }

    public SearchResult(String searchTerm, String category, String resultJson, LocalDateTime scrapedAt) {
        this.searchTerm = searchTerm;
        this.category = category;
        this.resultJson = resultJson;
        this.scrapedAt = scrapedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public LocalDateTime getScrapedAt() {
        return scrapedAt;
    }

    public void setScrapedAt(LocalDateTime scrapedAt) {
        this.scrapedAt = scrapedAt;
    }
}
