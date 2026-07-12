package com.pricewatch.repository;

import com.pricewatch.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    Optional<WatchlistItem> findByIdAndUserEmail(Long id, String userEmail);
}
