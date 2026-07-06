package com.pricewatch.repository;

import com.pricewatch.model.SearchResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SearchResultRepository extends JpaRepository<SearchResult, Long> {
    Optional<SearchResult> findBySearchTermAndCategory(String searchTerm, String category);
}
