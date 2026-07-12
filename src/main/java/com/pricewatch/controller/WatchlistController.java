package com.pricewatch.controller;

import com.pricewatch.model.WatchlistItem;
import com.pricewatch.repository.WatchlistItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {
    private final WatchlistItemRepository watchlistItemRepository;

    public WatchlistController(WatchlistItemRepository watchlistItemRepository) {
        this.watchlistItemRepository = watchlistItemRepository;
    }

    public record CreateWatchlistItemRequest(String userEmail, String productName, String category, String note) {
    }

    @GetMapping
    public List<WatchlistItem> list(@RequestParam String userEmail) {
        return watchlistItemRepository.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }

    @PostMapping
    public ResponseEntity<WatchlistItem> create(@RequestBody CreateWatchlistItemRequest request) {
        if (isBlank(request.userEmail()) || isBlank(request.productName())) {
            return ResponseEntity.badRequest().build();
        }

        String category = isBlank(request.category()) ? "GROCERY" : request.category();
        WatchlistItem item = new WatchlistItem(
            request.userEmail(),
            request.productName(),
            category,
            request.note(),
            LocalDateTime.now());
        return ResponseEntity.ok(watchlistItemRepository.save(item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam String userEmail) {
        return watchlistItemRepository.findByIdAndUserEmail(id, userEmail)
            .map(item -> {
                watchlistItemRepository.delete(item);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
